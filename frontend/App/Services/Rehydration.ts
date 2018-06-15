import ReduxPersist from '../Config/ReduxPersist'
import { AsyncStorage } from 'react-native'
import { persistStore } from 'redux-persist'
import StartupActions from '../Redux/StartupRedux'
import DebugConfig from '../Config/DebugConfig'

const updateReducers = (store: any) => {
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
      persistStore(store, null, startup).purge()
      AsyncStorage.setItem('reducerVersion', reducerVersion)
    } else {
      persistStore(store, null, startup)
    }
  }).catch(async () => {
    persistStore(store, null, startup).persist()
    AsyncStorage.setItem('reducerVersion', reducerVersion)
  })
}

export default { updateReducers }
