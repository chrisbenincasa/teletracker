import React from 'react'
import * as ReactNavigation from 'react-navigation'
import { connect } from 'react-redux'

// here is our redux-aware smart component
function ReduxNavigation (props) {
  const { dispatch, nav } = props
  return <AppNavigation navigation={{ dispatch, state: nav }} />
}

const mapStateToProps = state => ({ nav: state.nav })
export default connect(mapStateToProps)(ReduxNavigation)
