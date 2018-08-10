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
    divider: {
        backgroundColor: Colors.divider, 
        height: 1, 
        marginTop: 10
    },
    avatarContainer: {
        flexDirection: 'row', 
        marginTop: 5
    }
});
