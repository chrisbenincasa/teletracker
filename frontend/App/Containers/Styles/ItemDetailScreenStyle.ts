import { StyleSheet } from 'react-native';
import { ApplicationStyles, Colors } from '../../Themes/';

export default StyleSheet.create({
    ...ApplicationStyles.screen,
    coverContainer: {
        position: 'absolute',
        top: -20,
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
        borderColor: Colors.white
    },
    subHeaderContainer: {
        position: 'relative',
        top: 60,
        left: 0,
        backgroundColor: 'transparent',
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
    castHeader: {
        marginTop: 10,
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
    },
    genreContainer: {
        flex: 1, 
        flexDirection: 'row', 
        flexWrap: 'wrap', 
        marginLeft: 13, 
        marginRight: 15
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
    buttonsContainer: {
        flex: 1, 
        flexDirection: 'row', 
        marginVertical: 10
    },
    divider: {
        backgroundColor: Colors.divider, 
        height: 1, 
        marginTop: 10
    },
    descriptionContainer: {
        marginTop: 60, 
        marginHorizontal: 15
    }
});
