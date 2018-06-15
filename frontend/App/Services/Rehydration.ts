import { AsyncStorage } from 'react-native';
import { Persistor } from 'redux-persist';

import DebugConfig from '../Config/DebugConfig';
import StartupActions from '../Redux/StartupRedux';

const updateReducers = (store: any, persistor: Persistor) => {
  const reducerVersion = '1.0'
  const startup = () => store.dispatch(StartupActions.startup())

  // Check to ensure latest reducer version
  return AsyncStorage.getItem('reducerVersion').then((localVersion) => {
    if (localVersion !== reducerVersion) {
      if (DebugConfig.useReactotron) {
        console.tron.display({
          name: 'PURGE',
          value: {
            'Old Version:': localVersion,
            'New Version:': reducerVersion
          },
          preview: 'Reducer Version Change Detected',
          important: true
        })
      }
      // Purge store
      persistor.purge().then(startup);
      AsyncStorage.setItem('reducerVersion', reducerVersion)
    } else {
      startup();
    }
  }).catch(async () => {
    persistor.persist();
    startup();
    AsyncStorage.setItem('reducerVersion', reducerVersion)
  })
}

export default { updateReducers }
