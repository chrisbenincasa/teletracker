import { argv } from "yargs";
import * as progs from "./index";

async function run() {
  return progs[process.argv[2]](argv);
}

run()
  .then((result) => console.log(result))
  .catch((e) => console.error(e));
