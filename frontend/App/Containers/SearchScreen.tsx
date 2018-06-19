import React, { Component } from 'react';
import { View, Text, KeyboardAvoidingView, StyleSheet } from 'react-native';
import { connect } from 'react-redux';
import { Card, ListItem, Icon,  Header } from 'react-native-elements';
import HeaderLeft from '../Components/Header/HeaderLeft';
import HeaderCenter from '../Components/Header/HeaderCenter';
import HeaderRight from '../Components/Header/HeaderRight';
import Search from 'react-native-search-box';

// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
import styles from './Styles/SearchScreenStyle';

class SearchScreen extends Component {
  render () {
    console.log(styles);
    console.log(StyleSheet.flatten(styles.container));
    console.log("test");
    return (
      <View style={styles.container}>
      <Header>
        <HeaderLeft {...this.props} />
        <HeaderCenter />
        <HeaderRight />
      </Header> 
      <Search
          ref="search_box"
          backgroundColor='white'
          style={{flex:1}}
          /**
          * There are many props options:
          * https://github.com/agiletechvn/react-native-search-box
          */
        />
<Card> 
  </Card>
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

export default connect(mapStateToProps, mapDispatchToProps)(SearchScreen);
