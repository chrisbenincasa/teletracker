import { Storage } from "@google-cloud/storage";
import { KeyManagementServiceClient } from "@google-cloud/kms";
import crypto from "crypto";

const BERGLAS_PREFIX = "berglas://";
const METADATA_KMS_KEY = "berglas-kms-key";
const GCM_NONCE_SIZE = 12;

const algorithm = "aes-256-gcm";
const utf8 = "utf-8";
const base64 = "base64";

const client = new Storage();
const kmsClient = new KeyManagementServiceClient();

const decipher = (dek, cipherText) => {
  let nonce = cipherText.slice(0, GCM_NONCE_SIZE);
  let toDecrypt = cipherText.slice(GCM_NONCE_SIZE);

  let cipher = crypto.createCipheriv(algorithm, dek, nonce);
  return cipher.update(toDecrypt.slice(0, toDecrypt.length - 16));
};

const resolve = async (projectId, envVar) => {
  if (!envVar.startsWith(BERGLAS_PREFIX)) {
    return;
  }

  let withoutPrefix = envVar.substring(BERGLAS_PREFIX.length);
  let [bucket, object] = withoutPrefix.split("/", 2);

  let [file, metadata] = await client
    .bucket(bucket)
    .file(object)
    .get();

  let key = metadata.metadata[METADATA_KMS_KEY];

  let [contents, _] = await file.download();

  let [content1, content2] = contents.toString(utf8).split(":");

  let enc_dek = Buffer.from(content1, base64);
  let cipherText = Buffer.from(content2, base64);

  let [kmsResp, _1] = await kmsClient.decrypt({
    name: key,
    ciphertext: Buffer.from(enc_dek),
    additionalAuthenticatedData: Buffer.from(object)
  });

  return decipher(kmsResp.plaintext, cipherText);
};

const substitute = async () => {
  for (const envvar in process.env) {
    if (
      process.env.hasOwnProperty(envvar) &&
      process.env[envvar].startsWith(BERGLAS_PREFIX)
    ) {
      const element = process.env[envvar];
      let decrypted = await resolve("teletracker", element);
      process.env[envvar] = decrypted;
    }
  }
};

export { resolve, substitute };
