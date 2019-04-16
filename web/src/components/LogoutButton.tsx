import React, { Component } from "react";
import { connect } from "react-redux";
import { RouteComponentProps, withRouter } from "react-router";
import { bindActionCreators, Dispatch } from "redux";
import { logout } from "../reducers/auth";

interface AuthButtonProps {
  logout: () => void;
}

class AuthButton extends Component<
  RouteComponentProps<any> & AuthButtonProps
> {
  render() {
    return (
      <a
        href="#"
        onClick={e => {
          e.preventDefault();
          this.props.logout();
        }}
      >
        Sign out
      </a>
    );
  }
}

const mapDispatchToProps: (
  dispatch: Dispatch
) => AuthButtonProps = dispatch => {
  return bindActionCreators(
    {
      logout
    },
    dispatch
  );
};

export default withRouter(
  connect(
    null,
    mapDispatchToProps
  )(AuthButton)
);
