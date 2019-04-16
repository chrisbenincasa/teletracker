import { push } from 'connected-react-router';
import * as R from 'ramda';
import React, { Component, FormEvent } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import { AppState } from '../../reducers';
import { login } from '../../reducers/auth';

interface Props {
  isAuthed: boolean,

  login: (email: string, password: string) => void,

  changePage: () => void
}

interface State {
  email: string,
  password: string
}

class Home extends Component<Props, State> {
  state: State = {
    email: '',
    password: ''
  }

  onSubmit(ev: FormEvent<HTMLFormElement>) {
    ev.preventDefault();

    this.props.login(this.state.email, this.state.password);

    this.setState({
      email: '',
      password: ''
    });
  }

  render() {
    let { email, password } = this.state;

    return (
      <div>
        {this.props.isAuthed ? (
          <h1>Welcome Back</h1>
        ) : (
          <div>
            <h1>Log In!</h1>

            <form onSubmit={ev => this.onSubmit(ev)}>
              <div>
                <label>
                  Email:
                  <input
                    type="email"
                    name="email"
                    onChange={e => this.setState({ email: e.target.value })}
                    value={email}
                  />
                </label>
              </div>

              <div>
                <label>Password:</label>
                <input
                  type="password"
                  name="password"
                  onChange={e =>
                    this.setState({ password: e.target.value })
                  }
                  value={password}
                />
              </div>

              <input type="submit" value="Login" />
            </form>
          </div>
        )}
      </div>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState))
  }
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      login: (email: string, password: string) => login(email, password),
      changePage: () => push('/about-us')
    },
    dispatch,
  );

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(Home);
