import React, { Component } from 'react';
import { Image, KeyboardAvoidingView, Text, View, ScrollView } from 'react-native';
import { Badge, Button, Rating, Avatar, Divider, Icon } from 'react-native-elements';
import Header from '../Components/Header/Header';
import ViewMoreText from 'react-native-view-more-text';
import { Navigation } from 'react-native-navigation';
import getMetadata from '../Components/Helpers/getMetadata';
import { parseInitials } from '../Components/Helpers/textHelper';

import { Thing } from '../Model/external/themoviedb';
import ListActions from '../Redux/ListRedux';
import UserActions from '../Redux/UserRedux';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';

import headerStyles from '../Themes/ApplicationStyles';
import styles from './Styles/ItemDetailScreenStyle';


// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
interface Props {
    componentId: string,
    item: Thing,
    addItemToList: (componentId: string, listId: string, itemId: string | number) => any,
    markAsWatched: (componentId: string, itemId: string | number) => void
}

class ItemDetailScreen extends Component<Props> {
    constructor(props: Props) {
        super(props);

        this.state = {
            inList: false
        };
        
        // Disable menu swipe out on ItemDetailScreen
        Navigation.mergeOptions(this.props.componentId, {
            sideMenu: {
                left: {
                    visible: false,
                    enabled: false
                }
            }
        });
    }

    addItem() {
        this.props.addItemToList(this.props.componentId, 'default', this.props.item.id);

        this.setState({
            inList: !this.state.inList
          });
    }

    markAsWatched() {
        this.props.markAsWatched(this.props.componentId, this.props.item.id);
    }

    renderViewMore(onPress) {
        return (
            <Text 
                onPress={onPress}
                style={{
                    textDecorationLine: 'underline',
                    paddingTop: 5
            }}>
                View more
            </Text>
        )
      }

    renderViewLess(onPress) {
        return (
            <Text 
                onPress={onPress} 
                style={{
                    textDecorationLine: 'underline', 
                    paddingTop: 5
            }}>
                View less
            </Text>
        )
    }

    render () {
        return (
            <View style={styles.container}>
                <Header
                    outerContainerStyles={{
                        backgroundColor: 'transparent',
                        position: 'absolute',
                        zIndex: 999999
                    }}
                    statusBarProps={headerStyles.header.statusBarProps}
                    componentId={this.props.componentId}
                    leftComponent={{
                        icon: 'chevron-left',
                        back: true,
                        style: {
                            color: 'white'
                        }
                    }}
                    centerComponent={null} 
                    rightComponent={ null }
                />
                <ScrollView>
                    <KeyboardAvoidingView behavior='position'>
                        <View style={styles.coverContainer} >
                            {   // Check if cover image exists, otherwise show blue
                                getMetadata.getBackdropImagePath(this.props.item) ? 
                                    <Image source={{ 
                                        uri: 'https://image.tmdb.org/t/p/w500' + getMetadata.getBackdropImagePath(this.props.item)}}
                                        style={styles.coverImage}
                                    />
                                : <View style={styles.emptyCoverImage}></View> 
                            }
                        </View>
                        <View style={styles.subHeaderContainer}>
                            { getMetadata.getPosterPath(this.props.item)
                            ?
                                <Image 
                                    source={{ 
                                        uri: 'https://image.tmdb.org/t/p/w92' + getMetadata.getPosterPath(this.props.item)
                                    }} 
                                    style={styles.posterImage} />
                            : 
                                <View style={styles.posterImage}>
                                    <Icon
                                        name='image'
                                        color='#fff'
                                        size={50}
                                        containerStyle={{flex: 1}}
                                    />
                                </View>
                            }
                            <View style={styles.itemDetailsContainer}>
                                <Text style={{
                                    marginTop: 10,
                                    marginLeft: 10,
                                    fontSize: 20
                                }}>
                                    {this.props.item.name} 
                                    {
                                        getMetadata.getReleaseYear(this.props.item) 
                                        ? (
                                            <Text>({getMetadata.getReleaseYear(this.props.item)})</Text>
                                        )
                                        : null
                                    }
                                </Text>
                                <View style={styles.ratingsContainer}>
                                    <Rating
                                        type="star"
                                        fractions={1}
                                        startingValue={getMetadata.getRatingPath(this.props.item) / 2}
                                        readonly
                                        imageSize={15}
                                        style={{
                                            paddingBottom: 15,
                                            marginLeft: 10
                                        }}
                                    />
                                    <Text style={styles.ratingCount}>
                                        ({getMetadata.getVoteCount(this.props.item)})
                                    </Text>
                                </View>
                            </View>
                        </View>
                    </KeyboardAvoidingView>

                    <View style={styles.descriptionContainer}>
                        <ViewMoreText
                            numberOfLines={4}
                            renderViewMore={this.renderViewMore}
                            renderViewLess={this.renderViewLess}>
                            <Text>
                                {
                                    getMetadata.getDescription(this.props.item)
                                }
                            </Text>
                        </ViewMoreText>
                    </View>

                    {getMetadata.getGenre(this.props.item) &&
                        <View style={styles.genreContainer}>
                            {
                                getMetadata.getGenre(this.props.item)
                                ? getMetadata.getGenre(this.props.item).map((i) => (
                                <Badge
                                    key={i.id}
                                    value={i.name}
                                    textStyle={{ color: 'white' }}
                                    wrapperStyle={{
                                        marginHorizontal: 2,
                                        marginVertical: 5
                                    }}
                                />
                                )) 
                            : null}
                        </View>
                    }

                    <View style={styles.buttonsContainer}>
                        <Button 
                            title='Mark as Watched' 
                            icon={{
                                name: 'watch',
                                size: 25,
                                color: 'white'
                            }}
                            onPress={this.markAsWatched.bind(this)}
                            containerStyle={{marginHorizontal: 0}}>
                        </Button>
                        <Button 
                            title={ this.state.inList ? 'Remove'  : 'Track' }
                            onPress={ this.addItem.bind(this)} 
                            icon={{
                                name: this.state.inList ? 'clear' : 'add',
                                size: 25,
                                color: 'white'
                            }}
                            buttonStyle={{
                                backgroundColor: this.state.inList ? 'red' : 'green'}}
                            containerStyle={{marginHorizontal: 0}}>
                        </Button>
                        
                    </View>

                    {getMetadata.getSeasons(this.props.item) &&
                        <View style={styles.seasonsContainer}>
                            <Divider style={styles.divider} />
                            <Text style={styles.seasonsHeader}>Season Guide:</Text>
                            <ScrollView
                                horizontal={true}
                                showsHorizontalScrollIndicator={false}
                                style={styles.avatarContainer}>
                            {
                                getMetadata.getSeasons(this.props.item) ? getMetadata.getSeasons(this.props.item).map((i) => (
                                    <View>
                                        <Avatar
                                            key={i.id}
                                            large
                                            rounded
                                            source={
                                                i.poster_path
                                                ? {
                                                    uri: "https://image.tmdb.org/t/p/w92" + i.poster_path
                                                    } 
                                                : null}
                                            activeOpacity={0.7}
                                            title={i.poster_path ? null : parseInitials(i.name)}
                                            titleStyle={parseInitials(i.name).length > 2 ? {fontSize: 26} : null }
                                        />
                                        <Text style={styles.seasonsName}>{i.name}</Text>
                                    </View>
                                ))
                                : null
                            }
                            </ScrollView>
                        </View>
                    }

                    {getMetadata.getCast(this.props.item) &&
                        <View style={styles.castContainer}>
                            <Divider style={styles.divider} />
                            <Text style={styles.castHeader}>Cast:</Text>

                            <ScrollView
                                horizontal={true}
                                showsHorizontalScrollIndicator={false}
                                style={styles.avatarContainer}>
                            {
                                getMetadata.getCast(this.props.item).map((i) => (
                                    <View>
                                        <Avatar
                                            key={i.id}
                                            large
                                            rounded
                                            source={i.profile_path ? {uri: "https://image.tmdb.org/t/p/w92" + i.profile_path} : null}
                                            activeOpacity={0.7}
                                            title={i.poster_path ? null : parseInitials(i.name)}
                                            titleStyle={parseInitials(i.name).length > 2 ? {fontSize: 26} : null }
        
                                        />
                                        <Text style={styles.castName}>{i.name}</Text>
                                        <Text style={styles.castCharacter}>{i.character}</Text>
                                    </View>
                                ))
                            }
                            </ScrollView>
                        </View>
                    }
                </ScrollView>
            </View>
        )
    }
}

const mapStateToProps = (state) => {
    return {
    }
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
    return {
        addItemToList(componentId: string, listId: string, itemId: string | number) {
            dispatch(ListActions.addToList(componentId, listId, itemId));
        },
        markAsWatched(componentId: string, itemId: string | number) {
            dispatch(UserActions.postEvent(componentId, 'MarkedAsWatched', 'Show', itemId));
        }
    }
};

export default connect(mapStateToProps, mapDispatchToProps)(ItemDetailScreen);
