import React, { Component } from 'react';
import { ScrollView, Text, TextInput, View, KeyboardAvoidingView } from 'react-native';
import { connect, Dispatch, MapDispatchToProps } from 'react-redux';
import FullButton from '../Components/FullButton';
import UserActions, { SignupState } from '../Redux/UserRedux'
import { State as AppState } from '../Redux/State';

// Styles
import styles from './Styles/SignupScreenStyle';

interface State {
  username?: string,
  password?: string,
  email?: string
}

interface Props {
  signup: SignupState,
  navigation: any,
  onSignUpAttempt: any,
  onLoginPress: any
}

class SignupScreen extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { username: 'Christian', password: 'password', email: 'test@test.com' };
  }

  static get options() {
    return {
      topBar: {
        visible: false
      }
    };
  }

  render() {
    return (
      <ScrollView style={styles.container}>
        <Text 
            style={{fontSize: 27, textAlign: 'center', marginTop: 20}}>
            Sign Up!
        </Text>
        <TextInput 
          placeholder='Username' 
          style={{
            fontSize: 27, 
            flex: 1,
            borderColor: '#000', 
            borderStyle: 'solid', 
            borderWidth: 1, 
            padding: 10, 
            margin: 5
          }}
          editable={!this.props.signup.fetching}
          autoCapitalize='none'
          onChangeText={(username) => this.setState({ username })}
          value={this.state.username}
        />
        <TextInput 
          placeholder='Password' 
          style={{
            fontSize: 27, 
            flex: 1,
            borderColor: '#000', 
            borderStyle: 'solid', 
            borderWidth: 1, 
            padding: 10, 
            margin: 5
          }}
          editable={!this.props.signup.fetching}
          autoCapitalize='none'
          secureTextEntry={true}
          onChangeText={(password) => this.setState({ password })}
          value={this.state.password}
        />
        <TextInput 
          placeholder='Email' 
          style={{
            fontSize: 27, 
            flex: 1,
            borderColor: '#000', 
            borderStyle: 'solid', 
            borderWidth: 1, 
            padding: 10, 
            margin: 5,
          }}
          editable={!this.props.signup.fetching}
          autoCapitalize='none'
          onChangeText={(email) => this.setState({ email })}
          value={this.state.email}
        />
        <View style={{margin:7}} />
        <FullButton 
            text='Sign Up' 
            styles={{backgroundColor: 'red'}} 
            onPress={() => this.props.onSignUpAttempt(this.state)} 
        />
        <FullButton 
            text="Login"
            onPress={() => this.props.navigation.navigate('LoginScreen')} 
        />
      </ScrollView>
    )
  }
}

const mapStateToProps = (state: AppState) => {
  return {
    signup: state.user.signup
  }
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
  return {
    onSignUpAttempt: ({username, email, password}: State) => {
      dispatch(UserActions.userSignupRequest(username, email, password ));
    }
  }
};

export default connect(mapStateToProps, mapDispatchToProps)(SignupScreen);
