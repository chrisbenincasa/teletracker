import request from "request-promise";

const scheduleJob = async inputFile => {
  return request({
    uri: `${process.env.API_HOST}/api/v1/internal/run-task`,
    method: "POST",
    headers: {
      Authorization: `bearer ${process.env.ADMINISTRATOR_KEY}`
    },
    body: {
      className: process.env.JOB_CLASS_NAME,
      args: {
        inputFile: `gs://teletracker/${inputFile}`,
        dryRun: false
      }
    },
    json: true
  });
};

export { scheduleJob };
