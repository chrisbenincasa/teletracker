import { StyleSheet } from 'react-native';
import { ApplicationStyles, Colors } from '../../Themes';

export default StyleSheet.create({
    ...ApplicationStyles.screen,
    avatarContainer: {
        flexDirection: 'row', 
        marginTop: 5
    },
    seasonsContainer: {
        flex: 1, 
        marginHorizontal: 15
    },
    seasonsHeader: {
        marginTop: 10,
        fontSize: 16
    },
    seasonsName: {
        width:75, 
        textAlign: 'center', 
        fontWeight: 'bold'
    },
    divider: {
        backgroundColor: Colors.divider, 
        height: 1, 
        marginTop: 10
    }
});
