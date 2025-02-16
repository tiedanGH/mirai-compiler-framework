// 此文件为json.h：包含JSON编码(encodeToJson)和解析(decodeFromString)方法，使用时直接调用即可
// JSON目前只能像下面这样纯手动解析，因为glot上无法安装JSON拓展库
#include <iostream>
#include <map>
#include <any>
#include <vector>
#include <string>
#include <sstream>

class json {
  public:
    // 编码 map 至 JSON 字符串
    static std::string encodeToJson(const std::map<std::string, std::any>& map) {
        std::ostringstream oss;
        oss << "{";

        bool first = true;
        for (const auto& [key, value] : map) {
            if (!first) {
                oss << ",";
            }
            first = false;

            // 添加键
            oss << "\"" << key << "\":";
            // 添加值
            oss << anyToJson(value);
        }

        oss << "}";
        return oss.str();
    }

    // 解析 JSON 字符串至 map
    static std::map<std::string, std::any> decodeFromString(const std::string& str) {
        size_t pos = 0;
        return parseJsonObject(str, pos);
    }

  private:
    // 将 std::any 转换为 JSON 兼容的字符串
    static std::string anyToJson(const std::any& value) {
        try {
            if (value.type() == typeid(bool)) {
                return std::any_cast<bool>(value) ? "true" : "false";
            } else if (value.type() == typeid(int)) {
                return std::to_string(std::any_cast<int>(value));
            } else if (value.type() == typeid(double)) {
                return std::to_string(std::any_cast<double>(value));
            } else if (value.type() == typeid(std::string)) {
                return "\"" + std::any_cast<std::string>(value) + "\"";
            } else if (value.type() == typeid(std::map<std::string, std::any>)) {
                return mapToJson(std::any_cast<std::map<std::string, std::any>>(value));
            } else if (value.type() == typeid(std::vector<std::any>)) {
                return vectorToJson(std::any_cast<std::vector<std::any>>(value));
            } else {
                // 未知类型标记为 <unsupported type>
                return "\"<unsupported type>\"";
            }
        } catch (const std::bad_any_cast& e) {
            std::cerr << "Bad any_cast: " << e.what() << std::endl;
            return "\"<error>\"";
        }
    }

    // 将 std::map<std::string, std::any> 转换为 JSON 字符串
    static std::string mapToJson(const std::map<std::string, std::any>& map) {
        return encodeToJson(map);
    }

    // 将 std::vector<std::any> 转换为 JSON 字符串
    static std::string vectorToJson(const std::vector<std::any>& vec) {
        std::ostringstream oss;
        oss << "[";

        bool first = true;
        for (const auto& value : vec) {
            if (!first) {
                oss << ",";
            }
            first = false;

            oss << anyToJson(value);
        }

        oss << "]";
        return oss.str();
    }


    // 去掉字符串两端的空白字符
    static std::string trim(const std::string& str) {
        size_t first = str.find_first_not_of(" \t\n\r");
        if (first == std::string::npos) return "";
        size_t last = str.find_last_not_of(" \t\n\r");
        return str.substr(first, last - first + 1);
    }

    // 解析 JSON 对象
    static std::map<std::string, std::any> parseJsonObject(const std::string& str, size_t& pos) {
        std::map<std::string, std::any> obj;

        // 跳过起始的 '{'
        if (str[pos] != '{') throw std::invalid_argument("Expected '{' at position " + std::to_string(pos));
        ++pos;

        while (pos < str.length()) {
            // 跳过空白字符
            while (pos < str.length() && isspace(str[pos])) ++pos;

            // 检查结束
            if (str[pos] == '}') {
                ++pos; // 跳过 '}'
                break;
            }

            // 解析键
            if (str[pos] != '\"') throw std::invalid_argument("Expected '\"' at position " + std::to_string(pos));
            size_t keyStart = ++pos;
            size_t keyEnd = str.find('\"', keyStart);
            if (keyEnd == std::string::npos) throw std::invalid_argument("Unterminated string for key at position " + std::to_string(pos));
            std::string key = str.substr(keyStart, keyEnd - keyStart);
            pos = keyEnd + 1;

            // 跳过空白字符
            while (pos < str.length() && isspace(str[pos])) ++pos;

            // 跳过冒号
            if (str[pos] != ':') throw std::invalid_argument("Expected ':' at position " + std::to_string(pos));
            ++pos;

            // 跳过空白字符
            while (pos < str.length() && isspace(str[pos])) ++pos;

            // 解析值
            std::any value = parseJsonValue(str, pos);
            obj[key] = value;

            // 跳过空白字符
            while (pos < str.length() && isspace(str[pos])) ++pos;

            // 跳过逗号或结束
            if (str[pos] == ',') {
                ++pos; // 跳过 ','
            } else if (str[pos] == '}') {
                ++pos; // 跳过 '}'
                break;
            } else {
                throw std::invalid_argument("Expected ',' or '}' at position " + std::to_string(pos));
            }
        }

        return obj;
    }

    // 解析 JSON 字符串的值
    static std::any parseJsonValue(const std::string& str, size_t& pos) {
        while (pos < str.length() && isspace(str[pos])) ++pos;  // 跳过空白字符

        if (str[pos] == '{') {
            // 对象
            return parseJsonObject(str, pos);
        } else if (str[pos] == '\"') {
            // 字符串
            size_t start = ++pos;
            size_t end = str.find('\"', start);
            if (end == std::string::npos) throw std::invalid_argument("Unterminated string at position " + std::to_string(pos));
            std::string result = str.substr(start, end - start);
            pos = end + 1;
            return result;
        } else if (isdigit(str[pos]) || str[pos] == '-') {
            // 数字
            size_t start = pos;
            while (pos < str.length() && (isdigit(str[pos]) || str[pos] == '.' || str[pos] == '-')) ++pos;
            std::string numberStr = str.substr(start, pos - start);
            if (numberStr.find('.') != std::string::npos) {
                return std::stod(numberStr);
            } else {
                return std::stol(numberStr);
            }
        } else if (str.compare(pos, 4, "true") == 0) {
            pos += 4;
            return true;
        } else if (str.compare(pos, 5, "false") == 0) {
            pos += 5;
            return false;
        } else if (str.compare(pos, 4, "null") == 0) {
            pos += 4;
            return nullptr;
        } else {
            throw std::invalid_argument("Unexpected value at position " + std::to_string(pos));
        }
    }
};
