import {
  createStyles,
  LinearProgress,
  Theme,
  WithStyles,
  withStyles,
  Grid,
  Typography,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';
import { Thing, Availability } from '../../types';
import {
  retrieveUpcomingAvailability,
  retrieveAllAvailability,
} from '../../actions/availability';
import { AvailabilityState } from '../../reducers/availability';
import _ from 'lodash';
import ItemCard from '../../components/ItemCard';
import moment from 'moment';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    cardGrid: {
      padding: `${theme.spacing(1)}px`,
    },
  });

interface OwnProps extends WithStyles<typeof styles> {}

interface InjectedProps {
  isAuthed: boolean;
  isSearching: boolean;
  searchResults?: Thing[];
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
    this.props.retrieveUpcomingAvailability();
    this.props.retrieveAllAvailability();
  }

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderUpcoming = (upcoming: Availability[]) => {
    if (upcoming.length == 0) {
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

    let max = moment(
      _.maxBy(upcoming, av => moment(av.startDate).valueOf())!.startDate,
    );
    // let min = _.minBy(upcoming, av => moment(av.startDate).valueOf());
    // let diff = moment(max!.startDatez/x/).diff(firstMon);

    // console.log(moment.duration(diff));
    // let dur = moment.duration(diff);

    // let numDays = Math.ceil(dur.asDays());

    let start = firstMon;
    let end = firstMon.clone().add(1, 'weeks');

    while (start.isBefore(max)) {
      start = end.clone();
      end = end.add(1, 'weeks');
    }

    return _.chain(upcoming)
      .groupBy(av => {
        let m = moment(av.startDate);
        return m
          .subtract(m.weekday(), 'days')
          .startOf('day')
          .format();
      })
      .toPairs()
      .sortBy(([s, _]) => s)
      .reverse()
      .map(([key, avs]) => {
        let card = _.chain(avs)
          .groupBy(_.property('thing.id'))
          .map(things => {
            return (
              <ItemCard
                key={things[0].id}
                item={things[0].thing!}
                itemCardVisible={false}
                userSelf={this.props.userSelf!}
              />
            );
          })
          .value();

        return (
          <div
            key={moment(avs[0].startDate).format('MM/DD')}
            style={{ marginTop: 15 }}
          >
            <Typography variant="h4">
              Week of {moment(avs[0].startDate).format('MM/DD')}
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
    return this.props.isAuthed ? (
      !this.props.upcoming && !this.props.recentlyAdded ? (
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
              <h1 style={{ paddingLeft: 8 }}>Recently Added</h1>
              <div className={this.props.classes.cardGrid}>
                {this.renderUpcoming(this.props.recentlyAdded.availability)}
              </div>
            </div>
          ) : null}
        </div>
      )
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
  withStyles(styles)(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(New),
  ),
);
