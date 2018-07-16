import { StyleSheet } from 'react-native';
import { ApplicationStyles, Colors } from '../../Themes/';

export default StyleSheet.create({
    ...ApplicationStyles.screen,
    posterContainer: {
        flex: 1,
        // width: 110,
        // height: 153,
        width: 175,
        height: 243,
        backgroundColor: '#C9C9C9',
        alignContent: 'center'
    },
    defaultScreen: {
        flex: 1,
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center'
    },
    noResults: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center'
    },
    fetching: {
        flex: 1, 
        justifyContent: 'center',
        alignItems: 'center'
    },
    listTypeIcon: {
        backgroundColor: Colors.headerBackground,
        color: '#fff'
    }
});
