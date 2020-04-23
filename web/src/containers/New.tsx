import {
  createStyles,
  Grid,
  LinearProgress,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import _ from 'lodash';
import moment from 'moment';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import {
  retrieveAllAvailability,
  retrieveUpcomingAvailability,
} from '../actions/availability';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { AvailabilityState } from '../reducers/availability';
import ReactGA from 'react-ga';
import { Item } from '../types/v2/Item';

const styles = (theme: Theme) =>
  createStyles({
    cardGrid: {
      padding: theme.spacing(1),
    },
  });

interface OwnProps extends WithStyles<typeof styles> {}

interface InjectedProps {
  isAuthed: boolean;
  upcoming?: AvailabilityState;
  expiring?: AvailabilityState;
  recentlyAdded?: AvailabilityState;
}

interface DispatchProps {
  retrieveUpcomingAvailability: () => any;
  retrieveAllAvailability: () => any;
}

type Props = OwnProps & InjectedProps & DispatchProps & WithUserProps;

class New extends Component<Props> {
  componentDidMount() {
    const { isLoggedIn, userSelf } = this.props;

    this.props.retrieveUpcomingAvailability();
    // this.props.retrieveAllAvailability();

    ReactGA.pageview(window.location.pathname + window.location.search);

    if (
      isLoggedIn &&
      userSelf &&
      userSelf.user &&
      userSelf.user.getUsername()
    ) {
      ReactGA.set({ userId: userSelf.user.getUsername() });
    }
  }

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderUpcoming = (upcoming: Item[]) => {
    if (upcoming.length === 0) {
      return null;
    }

    let firstMon = moment();
    let currentMonth = firstMon.month() + 1;
    while (firstMon.month() + 1 === currentMonth) {
      let next = firstMon.weekday(-7);
      if (next.month() < currentMonth) {
        break;
      } else {
        firstMon = next;
      }
    }

    let allAvailabilties = _.chain(upcoming)
      .map(i => i.availability)
      .filter(av => !_.isUndefined(av))
      .map(av => av!)
      .flatten()
      .value();

    let max = moment(
      _.maxBy(allAvailabilties, av => moment(av!.start_date).valueOf())!
        .start_date,
    );

    let start = firstMon;
    let end = firstMon.clone().add(1, 'weeks');

    while (start.isBefore(max)) {
      start = end.clone();
      end = end.add(1, 'weeks');
    }

    console.log(allAvailabilties);

    return _.chain(allAvailabilties)
      .groupBy(av => {
        let m = moment(av!.start_date);
        return m
          .subtract(m.weekday(), 'days')
          .startOf('day')
          .format();
      })
      .toPairs()
      .sortBy(([s, _]) => s)
      .reverse()
      .map(([key, avs]) => {
        let start = moment(key);
        let end = moment(key)
          .add(1, 'weeks')
          .subtract(1, 'millisecond');
        console.log([start, end], avs);
        let card = _.chain(upcoming)
          .filter(thing => {
            return _.some(thing.availability, av => {
              return av.start_date
                ? moment(av.start_date).isBetween(start, end)
                : false;
            });
          })
          .map(thing => {
            return (
              <ItemCard
                key={thing.id}
                itemId={thing.id}
                userSelf={this.props.userSelf!}
              />
            );
          })
          .value();

        return (
          <div
            key={moment(avs[0]!.start_date).format('MM/DD')}
            style={{ marginTop: 15 }}
          >
            <Typography variant="h6">
              Week of {moment(avs[0]!.start_date).format('MM/DD')}
            </Typography>
            <Grid key={key} container spacing={2}>
              {card}
            </Grid>
          </div>
        );
      })
      .value();
  };

  render() {
    return !this.props.upcoming && !this.props.recentlyAdded ? (
      this.renderLoading()
    ) : (
      <div style={{ margin: 20 }}>
        {this.props.upcoming ? (
          <div className={this.props.classes.cardGrid}>
            {this.renderUpcoming(this.props.upcoming.availability)}
          </div>
        ) : null}
        {this.props.recentlyAdded ? (
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              flexGrow: 1,
            }}
          >
            <Typography style={{ paddingLeft: 8 }} variant="h4">
              Recently Added
            </Typography>
            <div className={this.props.classes.cardGrid}>
              {this.renderUpcoming(this.props.recentlyAdded.availability)}
            </div>
          </div>
        ) : null}
      </div>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    upcoming: appState.availability.upcoming,
    expiring: appState.availability.expiring,
    recentlyAdded: appState.availability.recentlyAdded,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      retrieveUpcomingAvailability,
      retrieveAllAvailability,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(connect(mapStateToProps, mapDispatchToProps)(New)),
);
