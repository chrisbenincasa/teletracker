import { StyleSheet } from 'react-native';
import { ApplicationStyles, Metrics, Colors } from '../../Themes';

export default StyleSheet.create({
  ...ApplicationStyles.screen,
  container: {
    flex: 1
  },
  row: {
    flex: 1,
    backgroundColor: Colors.fire,
    marginVertical: Metrics.smallMargin,
    justifyContent: 'center'
  },
  search: {
    backgroundColor: Colors.snow
  },
  boldLabel: {
    fontWeight: 'bold',
    alignSelf: 'center',
    color: Colors.charcoal,
    textAlign: 'center',
    marginBottom: Metrics.smallMargin
  },
  label: {
    textAlign: 'center',
    color: Colors.charcoal
  },
  listContent: {
    marginTop: 0//Metrics.baseMargin
  },
  username: {
    color: Colors.charcoal
  }
});
