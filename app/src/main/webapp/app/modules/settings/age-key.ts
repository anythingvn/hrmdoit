import { generateX25519Identity, identityToRecipient } from 'age-encryption';

export interface IAgeKeyPair {
  secretKey: string;
  publicKey: string;
}

/** Tạo cặp khóa age ngay trên trình duyệt; khóa bí mật không rời máy người dùng. */
export const createAgeKeyPair = async (): Promise<IAgeKeyPair> => {
  const secretKey = await generateX25519Identity();
  return { secretKey, publicKey: await identityToRecipient(secretKey) };
};

export const keyFileName = (isoDate: string) => `hrm-backup-key-${isoDate}.txt`;

/** Nội dung file khóa: chú thích (khóa công khai, cách giải mã) rồi khóa bí mật. */
export const keyFileText = (pair: IAgeKeyPair, isoDate: string) =>
  [
    `# created: ${isoDate}`,
    `# public key: ${pair.publicKey}`,
    `# age -d -i ${keyFileName(isoDate)} hrm-YYYY-MM-DD.tar.age | tar -x`,
    pair.secretKey,
    '',
  ].join('\n');
