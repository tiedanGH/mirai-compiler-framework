class Box:
    class MergeDirect:
        TO_BOTTOM = "to_bottom"
        TO_RIGHT = "to_right"

    def __init__(self):
        self.merge_direct = Box.MergeDirect.TO_BOTTOM
        self.merge_num = 1
        self.content = ""
        self.color = ""
        self.style = ""

    def set_content(self, content):
        assert self.merge_num > 0, "set_content失败，当前位置单元格已被merge覆盖"
        self.content = content
        return self

    def get_content(self):
        assert self.merge_num > 0, "get_content失败，当前位置单元格已被merge覆盖"
        return self.content

    def set_color(self, color):
        assert self.merge_num > 0, "set_color失败，当前位置单元格已被merge覆盖"
        self.color = color
        return self

    def set_style(self, style):
        self.style = style
        return self

    def is_visible(self):
        return self.merge_num >= 1


class Table:
    def __init__(self, row = 0, column = 0):
        self.boxes = [[Box() for _ in range(column)] for _ in range(row)]
        self.row = row
        self.column = column
        self.table_style = ' align="center" border="1px solid #ccc" cellspacing="0" cellpadding="0" '
        self.row_style = ' align="center" '

    def to_string(self):
        outstr = f'<table {self.table_style}><tbody>'
        for row in self.boxes:
            outstr += "\n<tr>"
            for box in row:
                if box.merge_num == 0:
                    continue
                cell = f'<td {self.row_style if not box.style else box.style}'
                if box.color:
                    cell += f' bgcolor="{box.color}"'
                if box.merge_num > 1:
                    direction = "rowspan" if box.merge_direct == Box.MergeDirect.TO_BOTTOM else "colspan"
                    cell += f' {direction}="{box.merge_num}"'
                cell += f'>\n{box.content}\n</td>'
                outstr += cell
            outstr += "\n</tr>"
        outstr += "\n</tbody></table>"
        return outstr

    def get(self, row, column):
        assert row < self.row, f"row需小于最大值 {row}"
        assert column < self.column, f"column需小于最大值 {row}"
        return self.boxes[row][column]

    def merge_down(self, row, column, num):
        if num == 1:
            return
        assert num > 1, "merge高度必须大于1"
        self.get(row, column).merge_num = num
        self.get(row, column).merge_direct = Box.MergeDirect.TO_BOTTOM
        for i in range(1, num):
            assert self.get(row + i, column).merge_num == 1, f"merge_down失败，位置({row + i},{column})的单元格已被merge覆盖"
            self.get(row + i, column).merge_num = 0

    def merge_right(self, row, column, num):
        if num == 1:
            return
        assert num > 1, "merge宽度必须大于1"
        self.get(row, column).merge_num = num
        self.get(row, column).merge_direct = Box.MergeDirect.TO_RIGHT
        for i in range(1, num):
            assert self.get(row, column + i).merge_num == 1, f"merge_right失败，位置({row + i},{column})的单元格已被merge覆盖"
            self.get(row, column + i).merge_num = 0

    def set_table_style(self, style):
        self.table_style = style

    def set_row_style(self, style):
        self.row_style = style

    def resize_row(self, row):
        assert row >= 1, "row不能小于1"
        while len(self.boxes) < row:
            self.boxes.append([Box() for _ in range(self.column)])
        self.row = row
