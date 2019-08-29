import {
  createStyles,
  Grid,
  LinearProgress,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import { Thing } from '../types';
import { retrievePopular } from '../actions/popular';
import ItemCard from '../components/ItemCard';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    title: {
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
    },
    card: {
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
    },
    cardMedia: {
      height: 0,
      width: '100%',
      paddingTop: '150%',
    },
    cardContent: {
      flexGrow: 1,
    },
  });

interface OwnProps extends WithStyles<typeof styles> {}

interface InjectedProps {
  isAuthed: boolean;
  isSearching: boolean;
  searchResults?: Thing[];
  popular?: string[];
  thingsBySlug: { [key: string]: Thing };
}

interface DispatchProps {
  retrievePopular: () => any;
}

type Props = OwnProps & InjectedProps & DispatchProps & WithUserProps;

class Popular extends Component<Props> {
  componentDidMount() {
    this.props.retrievePopular();
  }

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderPopular = () => {
    let { popular, userSelf, thingsBySlug } = this.props;

    return popular && popular && popular.length ? (
      <div style={{ padding: 8, margin: 20 }}>
        <Typography color="inherit" variant="h4">
          Popular
        </Typography>
        <Grid container spacing={2}>
          {popular.map(result => {
            let thing = thingsBySlug[result];
            if (thing) {
              return (
                <ItemCard
                  key={result}
                  userSelf={userSelf}
                  item={thing}
                  itemCardVisible={false}
                />
              );
            } else {
              return null;
            }
          })}
        </Grid>
      </div>
    ) : null;
  };

  render() {
    return this.props.isAuthed ? (
      <div style={{ display: 'flex', flexGrow: 1 }}>{this.renderPopular()}</div>
    ) : (
      <Redirect to="/login" />
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isSearching: appState.search.searching,
    searchResults: R.path<Thing[]>(['search', 'results', 'data'], appState),
    popular: appState.popular.popular,
    thingsBySlug: appState.itemDetail.thingsBySlug,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      retrievePopular,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(Popular),
  ),
);
