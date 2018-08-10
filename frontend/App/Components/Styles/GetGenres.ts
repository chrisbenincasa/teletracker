import { StyleSheet } from 'react-native';
import { ApplicationStyles, Colors } from '../../Themes';

export default StyleSheet.create({
    ...ApplicationStyles.screen,
    genreContainer: {
        flex: 1, 
        flexDirection: 'row', 
        flexWrap: 'wrap', 
        marginHorizontal: 15
    }
});
