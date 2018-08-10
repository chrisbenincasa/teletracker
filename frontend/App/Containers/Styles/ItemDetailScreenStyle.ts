import { StyleSheet } from 'react-native';
import { ApplicationStyles, Colors } from '../../Themes';

export default StyleSheet.create({
    ...ApplicationStyles.screen,
    container: {
        flex: 1,
        backgroundColor: '#fafafa'
    },
    coverContainer: {
        height: 195,
    },
    coverImage: {
        flex: 1,
        height: null,
        width: null,
        resizeMode: 'cover'
    },
    emptyCoverImage: {
        flex: 1, 
        width: '100%',
        height: '100%',
        backgroundColor: Colors.headerBackground
    },
    itemDetailsContainer: {
        flex: 1, 
        flexDirection: 'column', 
        alignItems: 'flex-start', 
        justifyContent: 'flex-end',
        marginHorizontal: 15,
        marginVertical: 5
    },
    ratingsContainer: {
        flexDirection: 'row'
    },
    ratingsCount: {
        fontSize: 10,
        fontStyle: 'italic'
    },
    descriptionContainer: {
        flex: 1,
        marginHorizontal: 15,
        flexDirection: 'column',
        justifyContent: 'flex-start'
    },
    posterImage: {
        flex: 1,
        width: 92,
        height: 138, 
        backgroundColor: '#C9C9C9'
    }
});
