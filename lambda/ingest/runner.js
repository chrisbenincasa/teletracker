import { argv } from 'yargs';
import { handleDirect } from './src';

handleDirect(argv)
  .then(x => {
    console.log(x);
  })
  .catch(e => {
    console.error(e);
  });
