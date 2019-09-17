import { Storage } from "@google-cloud/storage";
import { promises as fs } from "fs";

const storage = new Storage();
const bucket = storage.bucket("teletracker");

const writeResultsAndUploadToStorage = async (
  fileName,
  destinationDir,
  results
) => {
  await fs.writeFile(`/tmp/${fileName}`, JSON.stringify(results), "utf8");

  return uploadToStorage(fileName, destinationDir);
};

const uploadToStorage = async (fileName, destinationDir) => {
  return bucket.upload(`/tmp/${fileName}`, {
    gzip: true,
    contentType: "application/json",
    destination: destinationDir + "/" + fileName
  });
};

export { uploadToStorage, writeResultsAndUploadToStorage };
