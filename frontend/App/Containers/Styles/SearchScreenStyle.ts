import { StyleSheet } from 'react-native';
import { ApplicationStyles } from '../../Themes/';

export default StyleSheet.create({
    ...ApplicationStyles.screen,
    listContent: {
        justifyContent: 'space-around',
        flexDirection: 'column',
        alignContent: 'center'
    },
    posterContainer: {
        flex: 1,
        width: 110,
        height: 153,
        backgroundColor: '#C9C9C9',
        alignContent: 'center'
    }
});
