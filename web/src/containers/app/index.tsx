import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Link, Route } from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import LogoutButton from '../../components/LogoutButton';
import About from '../about';
import Home from '../home';
import { AppState } from '../../reducers';
import { checkAuth } from '../../reducers/auth';

interface OwnProps {
  isAuthed: boolean
}

interface DispatchProps {
  checkAuth: () => void
}

type Props = DispatchProps & OwnProps

class App extends Component<Props> {
  componentDidMount() {
    this.props.checkAuth();
  }

  render() {
    return (
      <div>
        <header>
          <Link to="/">Home</Link>
          <Link to="/about-us">About</Link>
          {(this.props.isAuthed) ? <LogoutButton /> : null}
        </header>

        <main>
          <Route exact path="/" component={Home} />
          <Route exact path="/about-us" component={About} />
          <Route exact path="/logout" component={About} />
        </main>
      </div>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: R.hasPath(["auth", "token"], appState)
  }; 
}

const mapDispatchToProps: (dispatch: Dispatch) => DispatchProps = (dispatch) => {
  return bindActionCreators(
    {
      checkAuth
    },
    dispatch
  )
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(App);