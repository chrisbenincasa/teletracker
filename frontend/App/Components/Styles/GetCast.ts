import { StyleSheet } from 'react-native';
import { ApplicationStyles, Colors } from '../../Themes';

export default StyleSheet.create({
    ...ApplicationStyles.screen,
    castHeader: {
        fontSize: 16
    },
    castContainer: {
        flex: 1, 
        marginHorizontal: 15
    },
    castName: {
        width: 75, 
        textAlign: 'center', 
        fontWeight: 'bold'
    },
    castCharacter: {
        width: 75, 
        textAlign: 'center', 
        fontSize: 10, 
        fontStyle: 'italic'
    },
    avatarContainer: {
        flexDirection: 'row', 
        marginTop: 5
    }
});
