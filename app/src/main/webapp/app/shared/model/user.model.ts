export interface IUser {
  id?: any;
  login?: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  activated?: boolean;
  langKey?: string;
  authorities?: any[];
  createdBy?: string;
  createdDate?: Date | null;
  lastModifiedBy?: string;
  lastModifiedDate?: Date | null;
  /** Chỉ gửi khi tạo: số CCCD, cũng là tên đăng nhập. */
  idNumber?: string;
  /** Chỉ gửi khi tạo: mật khẩu ban đầu (tùy chọn; trống = mặc định). */
  password?: string;
  /** Đang dùng mật khẩu mặc định (chỉ đọc). */
  mustChangePassword?: boolean;
}

export const defaultValue: Readonly<IUser> = {
  login: '',
  firstName: '',
  lastName: '',
  email: '',
  activated: true,
  langKey: 'vi',
  authorities: [],
  createdBy: '',
  createdDate: null,
  lastModifiedBy: '',
  lastModifiedDate: null,
  idNumber: '',
};
