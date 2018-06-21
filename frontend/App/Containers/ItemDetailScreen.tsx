import * as R from 'ramda';
import React, { Component } from 'react';
import { Image, KeyboardAvoidingView, Text, View } from 'react-native';
import { Button, Header } from 'react-native-elements';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';
import { Movie } from 'themoviedb-client-typed';

import { Thing } from '../Model/external/themoviedb';
import ListActions from '../Redux/ListRedux';
import headerStyles from '../Themes/ApplicationStyles';
import styles from './Styles/ItemDetailScreenStyle';


// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
interface Props {
  componentId: string,
  item: Thing,
  addItemToList: (componentId: string, listId: string, itemId: string | number) => any
}

class ItemDetailScreen extends Component<Props> {
  private tmdbMovieView = R.lensPath(['item', 'metadata', 'themoviedb', 'movie']);

  getImagePath() {
    let meta = this.props.item.metadata.themoviedb;
    if (this.hasTmdbMovie()) {
      return R.view<Props, Movie>(this.tmdbMovieView, this.props).poster_path;
    } else if (this.hasTmdbShow()) {
      return meta.show.poster_path;
    } else if (this.hasTmdbPerson()) {
      return meta.person.profile_path;
    }
  }

  hasTmdbMetadata() {
    return this.props.item.metadata && this.props.item.metadata.themoviedb;
  }

  hasTmdbMovie() {
    return this.hasTmdbMetadata() && this.props.item.metadata.themoviedb.movie;
  }

  hasTmdbShow() {
    return this.hasTmdbMetadata() && this.props.item.metadata.themoviedb.show;
  }
  
  hasTmdbPerson() {
    return this.hasTmdbMetadata() && this.props.item.metadata.themoviedb.person;
  }

  addItem() {
    this.props.addItemToList(this.props.componentId, '???', this.props.item.id);
  }

  render () {
    return (
      <View style={styles.container}>
        <Header
          outerContainerStyles={headerStyles.header.outer}
          innerContainerStyles={headerStyles.header.inner}
          statusBarProps={headerStyles.header.statusBarProps}
          componentId={this.props.componentId}
          leftComponent={{icon:  'chevron-left', style: { color: 'white' } }}
          centerComponent={{text: this.props.item.name,  style: { color: 'white' } }} />
        <KeyboardAvoidingView behavior='position'>
          <Image source={{ uri: 'https://image.tmdb.org/t/p/w185_and_h278_bestv2' + this.getImagePath()}} style={{width:185,height:278}} />
          <Text>{this.props.item.name}</Text>
          <Button title='Add' onPress={this.addItem.bind(this)}></Button>
        </KeyboardAvoidingView>
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
    }
  }
};

export default connect(mapStateToProps, mapDispatchToProps)(ItemDetailScreen);
