import { parseSharedDriveId } from './backup-api';

describe('parseSharedDriveId', () => {
  const ID = '0AAfBVsNi11ezUk9PVA';
  it.each([
    `https://drive.google.com/drive/folders/${ID}`,
    `https://drive.google.com/drive/u/1/folders/${ID}?usp=sharing`,
    `https://drive.google.com/drive/folders/${ID}#abc`,
    `  ${ID}  `,
  ])('tách đúng ID từ %s', input => {
    expect(parseSharedDriveId(input)).toBe(ID);
  });

  it.each(['https://example.com', '', 'a b', 'ngắn'])('trả null cho "%s"', input => {
    expect(parseSharedDriveId(input)).toBeNull();
  });
});
