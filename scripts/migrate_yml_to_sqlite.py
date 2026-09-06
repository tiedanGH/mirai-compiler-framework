#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.2.2 → 2.0.0 存储数据迁移脚本

把五份 yml 中的存储数据迁移进 2.0.0 的 SQLite 库：

    PastebinStorage.yml          ┐
    PastebinPlatformStorage.yml  ┴→ project_storage
    PastebinBucket.yml            → bucket / bucket_project / bucket_backup
    CodeCache.yml                 → code_cache
    ExtraData.yml (statistics)    → statistics / statistics_total

本脚本**不建表、不删表、不改 PRAGMA user_version**。
目标库必须是 2.0.0 首次启动自动创建的空库——这样 schema 只有一个来源，不会漂移。

    # 1. 让 2.0.0 在本地跑一次，生成空的 storage.db，随后停止
    # 2. 试运行（默认，一个字节都不写）
    python migrate_yml_to_sqlite.py --data-dir <yml目录> --db <空库路径>
    # 3. 确认报告无误后写入
    python migrate_yml_to_sqlite.py --data-dir <yml目录> --db <空库路径> --apply

⚠️ 数据来源必须是**迁移前的 yml 副本**：2.0.0 一旦启动，ExtraData.yml 会被重写，其中的 statistics 段随之丢失。务必先整份复制数据目录再启动 2.0.0。

依赖：PyYAML
"""

import argparse
import hashlib
import io
import json
import os
import re
import shutil
import sqlite3
import sys
import time

try:
    import yaml
except ImportError:
    sys.exit("缺少依赖：pip install PyYAML")


# ==================== 常量：必须与 Schema.kt 保持一致 ====================

SCHEMA_VERSION = 1
BACKUP_SLOTS = 3
GLOBAL_PLATFORM = ""
GLOBAL_USER_ID = 0
PLATFORM_QQ = "qq"

BUCKET_FIELDS = {"name", "password", "owner", "userID", "projects", "desc", "content", "encrypt", "salt"}
# salt 是 bcrypt 改造前的历史遗留字段，2.0.0 不再使用，迁移时丢弃但会在报告中点名
DROPPED_BUCKET_FIELDS = {"salt"}


class MigrationError(Exception):
    pass


# ==================== YAML 读取 ====================

_KV_LINE = re.compile(r'^(\s*)("(?:[^"\\]|\\.)*"|\'(?:[^\']|\'\')*\'|[^:]+): (.*)$')


def sanitize_tabs(text):
    """
    yamlkt 会把含 Tab 的字符串写成不加引号的 plain scalar，而 YAML 规范不允许 Tab 出现在那个位置，PyYAML 会直接拒绝解析整个文件。

    这里把这类值原地改写成带转义的双引号标量。JSON 的字符串转义是 YAML 双引号标量转义的子集，所以 json.dumps 的结果可以直接用。
    """
    out, fixed = [], 0
    for line in text.split("\n"):
        if "\t" not in line:
            out.append(line)
            continue
        m = _KV_LINE.match(line)
        if m is None or "\t" in m.group(2):
            # Tab 出现在键上或结构未知，交给 PyYAML 报错，不做猜测
            out.append(line)
            continue
        indent, key, value = m.groups()
        if value[:1] in ('"', "'"):
            out.append(line)        # 已是引号标量，PyYAML 能处理
            continue
        out.append("%s%s: %s" % (indent, key, json.dumps(value, ensure_ascii=False)))
        fixed += 1
    return "\n".join(out), fixed


def make_loader():
    """
    只保留 null 的隐式 resolver，其余（bool / int / float / timestamp）全部砍掉，未匹配的 plain scalar 一律落到默认的 str tag。

    这是整个脚本最关键的一处。直接用 yaml.safe_load 会静默损坏数据：
      · bucket.userID  →  int（实测 9/9 全中）
      · bucket.encrypt →  bool True（Kotlin 侧比较的是字符串 "true"，一旦写成 bool，加密存储库会被当成明文处理）
      · 项目名 key、storage 的 value 也有大量整数形态

    刻意保留 null，是为了能识别出「未转义的裸 null」这种历史坏数据，而不是把它悄悄读成字符串 "null"。
    """
    class StrictLoader(yaml.SafeLoader):
        pass

    StrictLoader.yaml_implicit_resolvers = {
        ch: [(tag, rx) for (tag, rx) in resolvers if tag == "tag:yaml.org,2002:null"]
        for ch, resolvers in yaml.SafeLoader.yaml_implicit_resolvers.items()
    }
    return StrictLoader


def load_yml(path, report):
    if not os.path.isfile(path):
        report.warn("源文件不存在，按空处理：%s" % os.path.basename(path))
        return {}
    raw = io.open(path, encoding="utf-8").read()
    text, fixed = sanitize_tabs(raw)
    if fixed:
        report.note("%s：修正了 %d 处含 Tab 的未加引号标量" % (os.path.basename(path), fixed))
    try:
        return yaml.load(text, Loader=make_loader()) or {}
    except Exception as exc:
        raise MigrationError("解析 %s 失败：%s" % (os.path.basename(path), exc))


# ==================== 取值与转换 ====================

def utf16_len(text):
    """Kotlin 的 String.length 是 UTF-16 码元数，Python 的 len() 是码点数，含 emoji 时不等"""
    return len(text.encode("utf-16-le")) // 2


def _is_risky(value):
    return value.lstrip("\\").lower() in ("null", "~")


def unescape(value):
    """YamlSafeValue.unescape 的等价实现：去掉为规避 yamlkt 裸 null 而加的那个反斜杠"""
    if value.startswith("\\") and _is_risky(value):
        return value[1:]
    return value


def as_text(value, where, nulls):
    if value is None:
        nulls.append(where)
        return ""
    if isinstance(value, str):
        return value
    raise MigrationError("%s 期望字符串，实际是 %s（%r）" % (where, type(value).__name__, value))


def as_int(value, where):
    try:
        return int(str(value))
    except (TypeError, ValueError):
        raise MigrationError("%s 期望整数，实际是 %r" % (where, value))


def as_float(value, where):
    try:
        return float(str(value))
    except (TypeError, ValueError):
        raise MigrationError("%s 期望数字，实际是 %r" % (where, value))


# ==================== 报告 ====================

class Report(object):
    def __init__(self):
        self.lines = []
        self.warnings = []

    def head(self, text):
        self.lines.append("")
        self.lines.append("=" * 60)
        self.lines.append(text)
        self.lines.append("=" * 60)

    def item(self, text):
        self.lines.append(text)

    def note(self, text):
        self.lines.append("  · " + text)

    def warn(self, text):
        self.warnings.append(text)
        self.lines.append("  ⚠ " + text)

    def render(self):
        return "\n".join(self.lines)


# ==================== 转换 ====================

class Plan(object):
    """待写入的全部行，同时也是校验时的期望值"""

    def __init__(self):
        self.storage = []          # (project, platform, user_id, content, content_len)
        self.bucket = []           # (id, name, password, owner, user_id, desc, content, len, encrypt, empty)
        self.bucket_project = []   # (bucket_id, project)
        self.bucket_backup = []    # (bucket_id, slot, name, time, content, content_len)
        self.code_cache = []       # (project, code, code_len)
        self.statistics = []       # (project, run, score, markdown, md_time, download, dl_time)
        self.totals = (0.0, 0.0, 0.0, 0.0, 0.0)


def build_plan(src, known_projects, drop_orphans, report):
    plan = Plan()
    nulls = []
    orphans = {"存储": set(), "代码缓存": set(), "项目统计": set(), "存储库关联": set()}

    def keep(project, kind):
        if project in known_projects:
            return True
        orphans[kind].add(project)
        return not drop_orphans

    # ---------- project_storage ----------
    qq_storage = src["PastebinStorage"].get("storage") or {}
    for project, users in qq_storage.items():
        project = str(project)
        if not keep(project, "存储"):
            continue
        for uid, value in (users or {}).items():
            uid = as_int(uid, "PastebinStorage[%s] 的用户号" % project)
            content = unescape(as_text(value, "PastebinStorage[%s][%s]" % (project, uid), nulls))
            if uid == GLOBAL_USER_ID:
                plan.storage.append((project, GLOBAL_PLATFORM, GLOBAL_USER_ID, content, utf16_len(content)))
            else:
                plan.storage.append((project, PLATFORM_QQ, uid, content, utf16_len(content)))

    platform_storage = src["PastebinPlatformStorage"].get("storage") or {}
    for platform, projects in platform_storage.items():
        platform = str(platform)
        if platform == GLOBAL_PLATFORM:
            raise MigrationError("其他平台存储中出现了空平台名，会与 global 行冲突")
        for project, users in (projects or {}).items():
            project = str(project)
            if not keep(project, "存储"):
                continue
            for uid, value in (users or {}).items():
                uid = as_int(uid, "PastebinPlatformStorage[%s][%s] 的用户号" % (platform, project))
                if uid == GLOBAL_USER_ID:
                    raise MigrationError("平台 %s 的项目 %s 出现用户号 0，会与 global 行冲突" % (platform, project))
                where = "PastebinPlatformStorage[%s][%s][%s]" % (platform, project, uid)
                content = unescape(as_text(value, where, nulls))
                plan.storage.append((project, platform, uid, content, utf16_len(content)))

    # ---------- bucket ----------
    buckets = src["PastebinBucket"].get("bucket") or {}
    backups = src["PastebinBucket"].get("backups") or {}
    unknown_fields = set()
    for raw_id, data in buckets.items():
        bucket_id = as_int(raw_id, "存储库编号")
        data = data or {}
        unknown_fields |= (set(data.keys()) - BUCKET_FIELDS)
        if not data:
            # 空 map 即历史上的「空置槽位」，2.0.0 用 empty 列表达
            plan.bucket.append((bucket_id, "", "", "", "", "", "", 0, 0, 1))
            continue
        where = "bucket[%d]" % bucket_id
        content = unescape(as_text(data.get("content", ""), where + ".content", nulls))
        plan.bucket.append((
            bucket_id,
            as_text(data.get("name", ""), where + ".name", nulls),
            as_text(data.get("password", ""), where + ".password", nulls),
            as_text(data.get("owner", ""), where + ".owner", nulls),
            as_text(data.get("userID", ""), where + ".userID", nulls),
            as_text(data.get("desc", ""), where + ".desc", nulls),
            content,
            utf16_len(content),
            1 if as_text(data.get("encrypt", ""), where + ".encrypt", nulls) == "true" else 0,
            0,
        ))
        seen = set()
        for project in as_text(data.get("projects", ""), where + ".projects", nulls).split(" "):
            if not project or project in seen:
                continue
            seen.add(project)
            if not keep(project, "存储库关联"):
                continue
            plan.bucket_project.append((bucket_id, project))

    bucket_ids = {row[0] for row in plan.bucket}
    for raw_id, slots in backups.items():
        bucket_id = as_int(raw_id, "备份的存储库编号")
        if bucket_id not in bucket_ids:
            report.warn("备份数据引用了不存在的存储库 %d，已跳过" % bucket_id)
            continue
        for slot, backup in enumerate(slots or []):
            if backup is None:
                continue
            if slot >= BACKUP_SLOTS:
                report.warn("存储库 %d 的备份槽位 %d 超出上限 %d，已跳过" % (bucket_id, slot, BACKUP_SLOTS))
                continue
            where = "backups[%d][%d]" % (bucket_id, slot)
            content = unescape(as_text(backup.get("content", ""), where + ".content", nulls))
            plan.bucket_backup.append((
                bucket_id,
                slot,
                as_text(backup.get("name", ""), where + ".name", nulls),
                as_int(backup.get("time", 0), where + ".time"),
                content,
                utf16_len(content),
            ))

    if unknown_fields:
        report.warn("存储库中存在 2.0.0 不再使用的字段，已丢弃：%s" % "、".join(sorted(unknown_fields)))
        unexpected = unknown_fields - DROPPED_BUCKET_FIELDS
        if unexpected:
            raise MigrationError("存储库中出现未知字段 %s，请先确认其含义再迁移" % "、".join(sorted(unexpected)))

    # ---------- code_cache ----------
    for project, code in (src["CodeCache"].get("CodeCache") or {}).items():
        project = str(project)
        if not keep(project, "代码缓存"):
            continue
        text = as_text(code, "CodeCache[%s]" % project, nulls)
        plan.code_cache.append((project, text, utf16_len(text)))

    # ---------- statistics ----------
    totals = [0.0, 0.0, 0.0, 0.0, 0.0]
    for project, stat in (src["ExtraData"].get("statistics") or {}).items():
        project = str(project)
        stat = stat or {}
        where = "statistics[%s]" % project
        run = as_float(stat.get("run", 0), where + ".run")
        score = as_float(stat.get("score", 0), where + ".score")
        markdown = as_float(stat["markdown"], where + ".markdown") if "markdown" in stat else None
        md_time = as_float(stat["mdTime"], where + ".mdTime") if "mdTime" in stat else None
        download = as_float(stat["download"], where + ".download") if "download" in stat else None
        dl_time = as_float(stat["dlTime"], where + ".dlTime") if "dlTime" in stat else None

        # 全局累计统计包含孤儿项目：它们的执行次数同样是历史的一部分，
        # 「删项目不减总数」是 2.0.0 刻意的设计
        totals[0] += run
        totals[1] += markdown or 0.0
        totals[2] += md_time or 0.0
        totals[3] += download or 0.0
        totals[4] += dl_time or 0.0

        if not keep(project, "项目统计"):
            continue
        plan.statistics.append((project, run, score, markdown, md_time, download, dl_time))

    plan.totals = tuple(totals)

    # ---------- 报告 ----------
    if nulls:
        report.warn("发现 %d 处未转义的裸 null，已按空串迁移：%s" % (len(nulls), "、".join(nulls[:5])))
    for kind, names in orphans.items():
        if not names:
            continue
        action = "已丢弃" if drop_orphans else "已保留"
        listed = "、".join(sorted(names)[:10])
        more = "…等 %d 项" % len(names) if len(names) > 10 else ""
        report.warn("孤儿%s（项目已不存在）%s：%s%s" % (kind, action, listed, more))

    return plan


# ==================== 目标库 ====================

EXPECTED_TABLES = [
    "meta", "project_storage", "bucket", "bucket_project",
    "bucket_backup", "code_cache", "statistics", "statistics_total",
]
DATA_TABLES = ["project_storage", "bucket", "bucket_project", "bucket_backup", "code_cache", "statistics"]


def check_target(conn, force, report):
    tables = {row[0] for row in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    missing = [t for t in EXPECTED_TABLES if t not in tables]
    if missing:
        raise MigrationError(
            "目标库缺少表 %s。本脚本不建表，请先用 2.0.0 启动一次生成空库" % "、".join(missing)
        )

    version = conn.execute("PRAGMA user_version").fetchone()[0]
    if version != SCHEMA_VERSION:
        raise MigrationError("目标库 user_version=%d，本脚本对应 %d" % (version, SCHEMA_VERSION))

    existing = {t: conn.execute("SELECT COUNT(*) FROM %s" % t).fetchone()[0] for t in DATA_TABLES}
    non_empty = {t: c for t, c in existing.items() if c}
    if non_empty:
        detail = "、".join("%s=%d" % kv for kv in sorted(non_empty.items()))
        if not force:
            raise MigrationError("目标库并非空库（%s）。确认要覆盖请加 --force" % detail)
        report.warn("目标库并非空库（%s），--force 已指定，将先清空这些表" % detail)

    initialized = conn.execute("SELECT value FROM meta WHERE key='initialized'").fetchone()
    if initialized is None or initialized[0] != "true":
        report.warn("目标库的 initialized 标记未置位，2.0.0 启动时会当作首次启动处理")

    return bool(non_empty)


def write_plan(conn, plan, clear_first):
    if clear_first:
        for table in DATA_TABLES:
            conn.execute("DELETE FROM %s" % table)
    conn.executemany("INSERT INTO project_storage VALUES(?,?,?,?,?)", plan.storage)
    conn.executemany("INSERT INTO bucket VALUES(?,?,?,?,?,?,?,?,?,?)", plan.bucket)
    conn.executemany("INSERT INTO bucket_project VALUES(?,?)", plan.bucket_project)
    conn.executemany("INSERT INTO bucket_backup VALUES(?,?,?,?,?,?)", plan.bucket_backup)
    conn.executemany("INSERT INTO code_cache VALUES(?,?,?)", plan.code_cache)
    conn.executemany("INSERT INTO statistics VALUES(?,?,?,?,?,?,?)", plan.statistics)
    conn.execute(
        "UPDATE statistics_total SET run=?, markdown=?, md_time=?, download=?, dl_time=? WHERE id=1",
        plan.totals,
    )


# ==================== 校验 ====================

def fingerprint(rows):
    digest = hashlib.sha256()
    for row in sorted(rows, key=lambda r: repr(r)):
        digest.update(repr(row).encode("utf-8"))
        digest.update(b"\x00")
    return digest.hexdigest()[:16]


def verify_written(conn, plan, report):
    """A 计数 + B 摘要：库里读回的每一行都必须与计划完全一致"""
    checks = [
        ("project_storage", "SELECT project, platform, user_id, content, content_len FROM project_storage", plan.storage),
        ("bucket", "SELECT id, name, password, owner, user_id, description, content, content_len, encrypt, empty FROM bucket", plan.bucket),
        ("bucket_project", "SELECT bucket_id, project FROM bucket_project", plan.bucket_project),
        ("bucket_backup", "SELECT bucket_id, slot, name, time, content, content_len FROM bucket_backup", plan.bucket_backup),
        ("code_cache", "SELECT project, code, code_len FROM code_cache", plan.code_cache),
        ("statistics", "SELECT project, run, score, markdown, md_time, download, dl_time FROM statistics", plan.statistics),
    ]
    ok = True
    for table, sql, expected in checks:
        actual = [tuple(r) for r in conn.execute(sql)]
        if len(actual) != len(expected):
            report.warn("[A] %s 行数不符：库中 %d，计划 %d" % (table, len(actual), len(expected)))
            ok = False
            continue
        fa, fe = fingerprint(actual), fingerprint(expected)
        flag = "✅" if fa == fe else "❌"
        report.note("%s %-16s %6d 行  摘要 %s" % (flag, table, len(actual), fa))
        if fa != fe:
            diff = set(map(repr, actual)) ^ set(map(repr, expected))
            report.warn("[B] %s 内容不符，差异 %d 条，样例：%s" % (table, len(diff), list(diff)[0][:160]))
            ok = False

    row = conn.execute("SELECT run, markdown, md_time, download, dl_time FROM statistics_total WHERE id=1").fetchone()
    if row is None or tuple(row) != plan.totals:
        report.warn("[A] statistics_total 不符：库中 %r，计划 %r" % (row and tuple(row), plan.totals))
        ok = False
    else:
        report.note("✅ %-16s run=%d markdown=%d download=%d" % ("statistics_total", row[0], row[1], row[3]))
    return ok


def verify_rebuild(conn, src, known_projects, drop_orphans, report):
    """
    C 反向重建：从库里读回，重建成与源 yml 同构的嵌套结构，逐项深度比较。
    这一级通过即证明「能从库里一字不差地还原源数据」。
    """
    def kept(project):
        return (not drop_orphans) or (project in known_projects)

    ok = True

    # --- 项目存储 ---
    rebuilt_qq, rebuilt_platform = {}, {}
    for project, platform, uid, content, _ in conn.execute(
        "SELECT project, platform, user_id, content, content_len FROM project_storage"
    ):
        if platform == GLOBAL_PLATFORM:
            rebuilt_qq.setdefault(project, {})[GLOBAL_USER_ID] = content
        elif platform == PLATFORM_QQ:
            rebuilt_qq.setdefault(project, {})[uid] = content
        else:
            rebuilt_platform.setdefault(platform, {}).setdefault(project, {})[uid] = content

    expect_qq = {}
    for project, users in (src["PastebinStorage"].get("storage") or {}).items():
        project = str(project)
        if not kept(project):
            continue
        expect_qq[project] = {
            int(str(k)): unescape(v if v is not None else "") for k, v in (users or {}).items()
        }
    if rebuilt_qq != expect_qq:
        report.warn("[C] QQ 存储重建不一致：%s" % _first_diff(expect_qq, rebuilt_qq))
        ok = False

    expect_platform = {}
    for platform, projects in (src["PastebinPlatformStorage"].get("storage") or {}).items():
        for project, users in (projects or {}).items():
            project = str(project)
            if not kept(project) or not users:
                continue
            expect_platform.setdefault(str(platform), {})[project] = {
                int(str(k)): unescape(v if v is not None else "") for k, v in users.items()
            }
    if rebuilt_platform != expect_platform:
        report.warn("[C] 其他平台存储重建不一致：%s" % _first_diff(expect_platform, rebuilt_platform))
        ok = False

    # --- 代码缓存 ---
    rebuilt_cache = {p: c for p, c, _ in conn.execute("SELECT project, code, code_len FROM code_cache")}
    # 代码缓存历史上从未经过转义，直接比对
    expect_cache = {
        str(p): (c if c is not None else "") for p, c in (src["CodeCache"].get("CodeCache") or {}).items()
        if kept(str(p))
    }
    if rebuilt_cache != expect_cache:
        report.warn("[C] 代码缓存重建不一致：%s" % _first_diff(expect_cache, rebuilt_cache))
        ok = False

    # --- 存储库正文与备份 ---
    source_buckets = {str(k): (v or {}) for k, v in (src["PastebinBucket"].get("bucket") or {}).items()}
    for bucket_id, content in conn.execute("SELECT id, content FROM bucket WHERE empty = 0"):
        source = source_buckets.get(str(bucket_id), {})
        if content != unescape(source.get("content") or ""):
            report.warn("[C] 存储库 %d 的正文重建不一致" % bucket_id)
            ok = False
    source_backups = {str(k): (v or []) for k, v in (src["PastebinBucket"].get("backups") or {}).items()}
    for bucket_id, slot, content in conn.execute("SELECT bucket_id, slot, content FROM bucket_backup"):
        slots = source_backups.get(str(bucket_id), [])
        source = slots[slot] if slot < len(slots) and slots[slot] else {}
        if content != unescape(source.get("content") or ""):
            report.warn("[C] 存储库 %d 槽位 %d 的备份正文重建不一致" % (bucket_id, slot))
            ok = False

    if ok:
        report.note("✅ 反向重建：库中数据可完整还原为源 yml 结构")
    return ok


def _first_diff(expected, actual):
    missing = [k for k in expected if k not in actual]
    extra = [k for k in actual if k not in expected]
    if missing:
        return "库中缺少 %r 等 %d 项" % (missing[0], len(missing))
    if extra:
        return "库中多出 %r 等 %d 项" % (extra[0], len(extra))
    for key in expected:
        if expected[key] != actual[key]:
            return "键 %r 的内容不同" % (key,)
    return "结构不同"


def sample_report(plan, report):
    """D 抽样：把极值样本摆出来供人眼确认"""
    if plan.storage:
        longest = max(plan.storage, key=lambda r: r[4])
        report.note("最长存储：%s [%s/%s] %d 字符" % (longest[0], longest[1] or "global", longest[2], longest[4]))
    if plan.code_cache:
        longest = max(plan.code_cache, key=lambda r: r[2])
        report.note("最长缓存：%s %d 字符" % (longest[0], longest[2]))

    def hits(rows, index, predicate):
        return [r for r in rows if predicate(r[index])]

    specials = [
        ("含 BOM", lambda s: s.startswith("﻿")),
        ("含 CRLF", lambda s: "\r\n" in s),
        ("含裸控制字符", lambda s: any(ord(c) < 32 and c not in "\r\n\t" for c in s)),
        ("含非 BMP 字符", lambda s: any(ord(c) > 0xFFFF for c in s)),
        ("含 Tab", lambda s: "\t" in s),
    ]
    for label, predicate in specials:
        n = len(hits(plan.code_cache, 1, predicate)) + len(hits(plan.storage, 3, predicate))
        if n:
            report.note("%s 的条目：%d 条（已原样迁移）" % (label, n))

    encrypted = [r for r in plan.bucket if r[8] == 1]
    if encrypted:
        report.note("加密存储库：%s（正文原样搬运，未解密）" % "、".join(str(r[0]) for r in encrypted))

    utf16_diff = sum(1 for _, code, ln in plan.code_cache if ln != len(code))
    if utf16_diff:
        report.note("UTF-16 长度与码点数不同的缓存条目：%d 条（content_len 取前者）" % utf16_diff)


# ==================== 主流程 ====================

def backup_sources(data_dir, report):
    stamp = time.strftime("%Y-%m-%d_%H.%M.%S")
    target = os.path.join(os.path.dirname(os.path.abspath(data_dir)),
                          "migration-backup-%s" % stamp)
    os.makedirs(target)
    for name in os.listdir(data_dir):
        path = os.path.join(data_dir, name)
        if os.path.isfile(path) and name.lower().endswith(".yml"):
            shutil.copy2(path, os.path.join(target, name))
    report.note("源 yml 已备份至 %s" % target)


def main():
    parser = argparse.ArgumentParser(description="把 1.2.2 的 yml 存储数据迁移到 2.0.0 的 SQLite 库")
    parser.add_argument("--data-dir", required=True, help="迁移前的 yml 目录（务必是副本）")
    parser.add_argument("--db", required=True, help="2.0.0 首次启动生成的空 storage.db")
    parser.add_argument("--apply", action="store_true", help="真正写入；不加此参数只做试运行")
    parser.add_argument("--force", action="store_true", help="允许写入非空库（会先清空业务表）")
    parser.add_argument("--keep-orphans", action="store_true", help="保留项目已不存在的孤儿数据")
    parser.add_argument("--no-backup", action="store_true", help="跳过源 yml 备份")
    args = parser.parse_args()

    report = Report()
    report.head("1.2.2 → 2.0.0 存储数据迁移%s" % ("" if args.apply else "（试运行，不写入任何数据）"))

    if not os.path.isdir(args.data_dir):
        raise MigrationError("数据目录不存在：%s" % args.data_dir)
    if not os.path.isfile(args.db):
        raise MigrationError("目标库不存在：%s（请先用 2.0.0 启动一次生成空库）" % args.db)

    report.head("读取源数据")
    src = {}
    for name in ("PastebinStorage", "PastebinPlatformStorage", "PastebinBucket", "CodeCache", "ExtraData", "PastebinData"):
        src[name] = load_yml(os.path.join(args.data_dir, name + ".yml"), report)
    known_projects = set(str(k) for k in (src["PastebinData"].get("pastebin") or {}).keys())
    report.note("项目总数：%d" % len(known_projects))

    report.head("转换")
    plan = build_plan(src, known_projects, not args.keep_orphans, report)
    report.note("project_storage : %d 行" % len(plan.storage))
    report.note("bucket          : %d 行（含空置槽位）" % len(plan.bucket))
    report.note("bucket_project  : %d 行" % len(plan.bucket_project))
    report.note("bucket_backup   : %d 行" % len(plan.bucket_backup))
    report.note("code_cache      : %d 行" % len(plan.code_cache))
    report.note("statistics      : %d 行" % len(plan.statistics))
    report.note("statistics_total: run=%.0f markdown=%.0f download=%.0f" %
                (plan.totals[0], plan.totals[1], plan.totals[3]))

    report.head("抽样")
    sample_report(plan, report)

    conn = sqlite3.connect(args.db)
    conn.isolation_level = None
    try:
        report.head("目标库检查")
        had_data = check_target(conn, args.force, report)
        report.note("目标库通过检查")

        if args.apply and not args.no_backup:
            backup_sources(args.data_dir, report)

        report.head("写入与校验")
        conn.execute("BEGIN")
        write_plan(conn, plan, clear_first=had_data)
        ok = verify_written(conn, plan, report)
        ok = verify_rebuild(conn, src, known_projects, not args.keep_orphans, report) and ok

        if not ok:
            conn.execute("ROLLBACK")
            report.head("❌ 校验未通过，已回滚，目标库未被改动")
            print(report.render())
            return 1

        if args.apply:
            conn.execute("COMMIT")
            report.head("✅ 迁移完成并已提交")
        else:
            conn.execute("ROLLBACK")
            report.head("✅ 试运行通过，已回滚。确认无误后加 --apply 正式写入")
    finally:
        conn.close()

    if report.warnings:
        report.item("")
        report.item("共 %d 条提示需要确认：" % len(report.warnings))
        for text in report.warnings:
            report.item("  ⚠ " + text)

    print(report.render())
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except MigrationError as exc:
        sys.exit("\n❌ 迁移中止：%s" % exc)
