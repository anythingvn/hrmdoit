"""Xuất sheet 'Bảng lương' (giá trị đã tính) ra TSV có kiểu, điền giá trị ô gộp. Tùy chọn ẩn danh tên."""
import sys, datetime, openpyxl
src, dst, anon = sys.argv[1], sys.argv[2], len(sys.argv) > 3
wb = openpyxl.load_workbook(src, data_only=True)
ws = wb['Bảng lương']
grid = {}
for row in ws.iter_rows():
    for c in row:
        grid[(c.row, c.column)] = c.value
for rng in ws.merged_cells.ranges:
    v = grid.get((rng.min_row, rng.min_col))
    for r in range(rng.min_row, rng.max_row + 1):
        for cc in range(rng.min_col, rng.max_col + 1):
            grid[(r, cc)] = v
names = {}
def enc(v, r, c):
    if v is None: return ''
    if isinstance(v, (datetime.datetime, datetime.date)): return 'd:' + v.strftime('%Y-%m-%d')
    if isinstance(v, bool): return 's:' + str(v)
    if isinstance(v, (int, float)): return 'n:' + repr(v)
    s = str(v)
    if s.startswith('#'): return 'e:' + s
    if anon and c == 5 and r >= 10 and s.strip() and s != 'Họ và tên':
        names.setdefault(s, 'Nhân viên %02d' % (len(names) + 1)); s = names[s]
    return 's:' + s.replace('\t', ' ').replace('\n', '\\n')
with open(dst, 'w', encoding='utf8') as f:
    for r in range(1, ws.max_row + 1):
        f.write('\t'.join(enc(grid.get((r, c)), r, c) for c in range(1, ws.max_column + 1)) + '\n')
print('rows', ws.max_row, 'cols', ws.max_column)
