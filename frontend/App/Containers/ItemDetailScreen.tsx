import React, { Component } from 'react';
import { KeyboardAvoidingView, Text, View, Image } from 'react-native';
import { connect } from 'react-redux';

import styles from './Styles/ItemDetailScreenStyle';
import headerStyles from '../Themes/ApplicationStyles';
import * as Model from '../Model/external/themoviedb';
import { Header, Icon } from 'react-native-elements';


// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
interface Props {
  item: Model.Movie | Model.TvShow | Model.Person
}

class ItemDetailScreen extends Component<Props> {
  titleString() {
    if (Model.Guards.isMovie(this.props.item)) {
      return this.props.item.title;
    } else if (Model.Guards.isTvShow(this.props.item)) {
      return this.props.item.name;
    } else {
      return this.props.item.name;
    }
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
          centerComponent={{text: this.titleString(),  style: { color: 'white' } }} />
        <KeyboardAvoidingView behavior='position'>
          <Image source={{ uri: 'https://image.tmdb.org/t/p/w185_and_h278_bestv2' + this.props.item.poster_path }} style={{width:185,height:278}} />
          <Text>{this.titleString()}</Text>
        </KeyboardAvoidingView>
      </View>
    )
  }
}

const mapStateToProps = (state) => {
  return {
  }
};

const mapDispatchToProps = (dispatch) => {
  return {
  }
};

export default connect(mapStateToProps, mapDispatchToProps)(ItemDetailScreen);
