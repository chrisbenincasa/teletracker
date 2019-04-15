import React, { Component } from 'react'
import { Route, Link } from 'react-router-dom'
import Home from '../home'
import About from '../about'
import { connect } from 'react-redux'
import { Dispatch, bindActionCreators } from 'redux';
import { checkAuth } from '../../modules/counter';

// const App = () => (
  
// )

interface OwnProps {
  
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
        </header>

        <main>
          <Route exact path="/" component={Home} />
          <Route exact path="/about-us" component={About} />
        </main>
      </div>
    );
  }
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
  null,
  mapDispatchToProps
)(App);