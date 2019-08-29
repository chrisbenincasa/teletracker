import React from 'react';
import { AppState } from '../reducers';
import { bindActionCreators, Dispatch } from 'redux';
import { Redirect, RouteComponentProps, withRouter } from 'react-router';
import { connect } from 'react-redux';
import {
  personFetchInitiated,
  PersonFetchInitiatedPayload,
} from '../actions/people/get_person';
import { Person } from '../types';
import * as R from 'ramda';
import { LinearProgress, Typography } from '@material-ui/core';

interface OwnProps {}

interface StateProps {
  isAuthed: boolean;
  person?: Person;
}

interface DispatchProps {
  personFetchInitiated: (id: PersonFetchInitiatedPayload) => void;
}

interface RouteProps {
  id: string;
}

type NotOwnProps = DispatchProps & RouteComponentProps<RouteProps>;

type Props = OwnProps & StateProps & NotOwnProps;

class PersonDetail extends React.Component<Props> {
  componentDidMount(): void {
    this.props.personFetchInitiated({ id: this.props.match.params.id });
  }

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderPerson() {
    let { person } = this.props;

    if (!person) {
      return this.renderLoading();
    }

    return !person ? (
      this.renderLoading()
    ) : (
      <Typography>{person.name}</Typography>
    );
  }

  render() {
    let { isAuthed } = this.props;

    return isAuthed ? (
      <div style={{ display: 'flex', flexGrow: 1 }}>{this.renderPerson()}</div>
    ) : (
      <Redirect to="/login" />
    );
  }
}

const mapStateToProps: (
  initialState: AppState,
  props: NotOwnProps,
) => (appState: AppState) => StateProps = (initial, props) => appState => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    // isFetching: appState.itemDetail.fetching,
    person:
      appState.people.peopleById[props.match.params.id] ||
      appState.people.peopleBySlug[props.match.params.id],
  };
};

const mapDispatchToProps: (dispatch: Dispatch) => DispatchProps = dispatch =>
  bindActionCreators(
    {
      personFetchInitiated,
    },
    dispatch,
  );

export default withRouter(
  connect(
    mapStateToProps,
    mapDispatchToProps,
  )(PersonDetail),
);
