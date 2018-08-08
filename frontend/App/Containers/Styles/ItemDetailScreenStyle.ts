import { StyleSheet } from 'react-native';
import { ApplicationStyles, Colors } from '../../Themes';

export default StyleSheet.create({
    ...ApplicationStyles.screen,
    container: {
        flex: 1,
        backgroundColor: '#fafafa'
    },
    coverContainer: {
        position: 'absolute',
        top: 0,
        left: 0,
        width: '100%',
        height: '100%'
    },
    coverImage: {
        flex: 1, 
        width: '100%',
        height: '100%',
        resizeMode: 'cover'
    },
    emptyCoverImage: {
        flex: 1, 
        width: '100%',
        height: '100%',
        backgroundColor: Colors.headerBackground
    },
    posterImage: {
        width: 92,
        height: 138, 
        marginTop: 20,
        marginLeft: 12,
        borderWidth: 2,
        borderColor: Colors.white,
        backgroundColor: '#C9C9C9', 
        alignContent: 'center'
    },
    subHeaderContainer: {
        position: 'relative',
        top: 60,
        left: 0,
        flexDirection: 'row'
    },
    itemDetailsContainer: {
        flex: 1, 
        flexDirection: 'column', 
        alignItems: 'flex-start', 
        justifyContent: 'flex-end'
    },
    ratingsContainer: {
        flexDirection: 'row'
    },
    ratingsCount: {
        fontSize: 10,
        marginLeft: 10,
        fontStyle: 'italic'
    },
    descriptionContainer: {
        marginTop: 60, 
        marginHorizontal: 15
    }
});
