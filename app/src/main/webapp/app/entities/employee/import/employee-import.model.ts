export interface IEmployeeImportRow {
  excelRow: number;
  fullName: string;
  idNumber: string | null;
  personType: string;
  /** Mã dự kiến hoặc mã ghi tay; null cho HĐ dịch vụ (tự sinh khi lưu). */
  code: string | null;
  codeProvisional: boolean;
  departmentName: string | null;
  managerRef: string | null;
}

export interface IEmployeeImportIssue {
  severity: 'ERROR' | 'WARNING';
  /** 0 = lỗi cấp file. */
  excelRow: number;
  fullName: string | null;
  field: string | null;
  fieldLabel: string | null;
  message: string;
}

export interface IEmployeeImportNewDepartment {
  name: string;
  excelRows: number[];
}

export interface IEmployeeImportCheck {
  rowCount: number;
  rows: IEmployeeImportRow[];
  issues: IEmployeeImportIssue[];
  newDepartments: IEmployeeImportNewDepartment[];
  errorCount: number;
  warningCount: number;
  canSave: boolean;
}

export interface IEmployeeImportCreated {
  excelRow: number;
  fullName: string;
  code: string;
}

export interface IEmployeeImportResult {
  createdCount: number;
  created: IEmployeeImportCreated[];
  createdDepartments: string[];
}
