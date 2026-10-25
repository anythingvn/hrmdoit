import React from 'react';

export interface ISectionCardProps extends Omit<React.HTMLAttributes<HTMLElement>, 'title'> {
  /** Tiêu đề thẻ (h2 mặc định). */
  title?: React.ReactNode;
  titleId?: string;
  headingLevel?: 2 | 3;
  /** Link phụ / nút bên phải tiêu đề, vd "Xem tất cả". */
  actions?: React.ReactNode;
  /** Dòng mô tả nhạt dưới tiêu đề. */
  subtitle?: React.ReactNode;
  /** Thẻ tối (canvas "Chấm công hôm nay"). */
  dark?: boolean;
  /** Không đệm (bảng sát mép thẻ). */
  flush?: boolean;
  as?: 'section' | 'div' | 'article';
}

/** Thẻ trắng bo góc trên nền be, có tiêu đề và hành động phụ bên phải. */
export const SectionCard = ({
  title,
  titleId,
  headingLevel = 2,
  actions,
  subtitle,
  dark,
  flush,
  as = 'section',
  className,
  children,
  ...rest
}: ISectionCardProps) => {
  const Tag = as;
  const Heading = headingLevel === 3 ? 'h3' : 'h2';
  const classes = ['doit-card', dark ? 'doit-card-dark' : '', flush ? 'doit-card-flush' : '', className ?? ''].filter(Boolean).join(' ');
  const labelled = title && titleId && as === 'section' && !rest['aria-label'] ? { 'aria-labelledby': titleId } : {};
  return (
    <Tag className={classes} {...labelled} {...rest}>
      {(title || actions) && (
        <div className="doit-card-head">
          {title && (
            <Heading className="doit-card-title" id={titleId}>
              {title}
            </Heading>
          )}
          {actions && <div className="doit-card-actions">{actions}</div>}
        </div>
      )}
      {subtitle && <p className="doit-card-sub">{subtitle}</p>}
      {children}
    </Tag>
  );
};

export default SectionCard;
