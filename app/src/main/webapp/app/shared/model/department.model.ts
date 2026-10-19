export interface IDepartment {
  id?: number;
  name?: string;
  active?: boolean;
}

export const defaultValue: Readonly<IDepartment> = {
  active: false,
};
