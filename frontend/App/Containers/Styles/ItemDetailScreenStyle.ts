import { StyleSheet } from 'react-native';
import { ApplicationStyles } from '../../Themes/';

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
        backgroundColor: '#476DC5'
    },
    posterImage: {
        width: 92,
        height: 138, 
        marginTop: 20,
        marginLeft: 12,
        borderWidth: 2,
        borderColor: '#fff'
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
    ratingCount: {
        fontSize: 10,
        marginLeft: 10,
        fontStyle: 'italic'
    },
    itemDescriptionContainer: {
        marginTop: 60
    },
    castHeader: {
        marginTop: 10,
        marginLeft: 12,
        fontSize: 16
    },
    avatarContainer: {
        flexDirection: 'row', 
        marginTop: 5, 
        marginLeft: 12
    }
});
