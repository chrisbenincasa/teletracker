import {getDirectoryS3} from "../../common/storage";
import {DATA_BUCKET} from "../../common/constants";
import moment from "moment";

export default async function watch(event) {
  try {
    if (!event.expectedSize) {
      throw new Error('Need to pass expected size');
    }

    let today = moment().format('YYYY-MM-DD');
    let x = await getDirectoryS3(DATA_BUCKET, `scrape-results/hulu/${today}/catalog`);
    console.log(x.length);
  } catch (e) {
    console.error(e);
    throw e;
  }
}