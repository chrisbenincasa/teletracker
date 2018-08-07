import { StyleSheet } from 'react-native';
import { ApplicationStyles, Colors } from '../../Themes/';

export default StyleSheet.create({
    ...ApplicationStyles.screen,
    container: {
        flex: 1,
        backgroundColor: '#fafafa'
    },
    addToList: {
        position: 'absolute',
        top: 0,
        right: 10,
        zIndex: 9999,
        opacity: .60,
        backgroundColor: '#000'
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
