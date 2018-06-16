import React, { Component } from 'react';
import { View, Text, KeyboardAvoidingView } from 'react-native';
import { connect } from 'react-redux';
import { Header } from 'react-native-elements';

// Add Actions - replace 'Your' with whatever your reducer is called :)
// import YourActions from '../Redux/YourRedux'

// Styles
import styles from './Styles/ItemDetailScreenStyle';

class ItemDetailScreen extends Component {
  render () {
    return (
      <View style={styles.container}>
        <Header
          leftComponent={{ icon: 'menu', color: '#fff' }}
          centerComponent={{ text: 'Item Details', style: { color: '#fff' } }}
          rightComponent={{ icon: 'home', color: '#fff' }}
        />
        <KeyboardAvoidingView behavior='position'>
          <Text>ItemDetailScreen</Text>
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
