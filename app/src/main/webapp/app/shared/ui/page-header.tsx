import React from 'react';

import { hasContent } from './has-content';

export interface IPageHeaderProps {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  /** Cụm nút hành động bên phải cùng hàng tiêu đề (ô tìm kiếm, nút "+ Thêm ..."). */
  actions?: React.ReactNode;
  /** id của thẻ tiêu đề (aria-labelledby, đường dẫn neo). */
  titleId?: string;
  /** Thuộc tính thêm cho thẻ tiêu đề (vd data-cy). */
  titleProps?: React.HTMLAttributes<HTMLHeadingElement> & { [key: `data-${string}`]: string };
  /** Mặc định h1; trang con nằm trong một trang có tiêu đề riêng thì dùng h2. */
  as?: 'h1' | 'h2';
  className?: string;
}

/** Tiêu đề trang theo canvas: 3 gạch cam, tiêu đề lớn đậm, dòng phụ nhạt, hành động bên phải. */
export const PageHeader = ({ title, subtitle, actions, titleId, titleProps, as = 'h1', className }: IPageHeaderProps) => {
  const Heading = as;
  return (
    <header className={`doit-page-header${className ? ` ${className}` : ''}`}>
      <div className="doit-page-header-text">
        <div className="doit-page-bars" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
        <Heading id={titleId} {...titleProps} className={`doit-page-title${titleProps?.className ? ` ${titleProps.className}` : ''}`}>
          {title}
        </Heading>
        {hasContent(subtitle) && <p className="doit-page-subtitle">{subtitle}</p>}
      </div>
      {hasContent(actions) && <div className="doit-page-actions">{actions}</div>}
    </header>
  );
};

export default PageHeader;
