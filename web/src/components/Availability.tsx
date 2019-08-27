import {
  CardContent,
  Chip,
  Collapse,
  createStyles,
  Tabs,
  Tab,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { Subscriptions, AttachMoney, Cloud } from '@material-ui/icons';
import * as R from 'ramda';
import { Availability, Network, Thing } from '../types';
import React, { Component } from 'react';

const styles = (theme: Theme) =>
  createStyles({
    availabilePlatforms: {
      display: 'flex',
      justifyContent: 'flex-start',
      [theme.breakpoints.down('sm')]: {
        justifyContent: 'center',
      },
      flexWrap: 'wrap',
    },
  });

interface Props extends WithStyles<typeof styles> {
  itemDetail: Thing;
  userSelf: any;
}

interface State {
  openTab: number;
}

class ThingAvailability extends Component<Props, State> {
  state: State = {
    openTab: 0,
  };

  constructor(props: Props) {
    super(props);
  }

  manageAvailabilityTabs = value => {
    this.setState({ openTab: value });
  };

  renderOfferDetails = (availabilities: Availability[]) => {
    let { userSelf } = this.props;

    let preferences = userSelf.preferences;
    let networkSubscriptions = userSelf.networks;
    let onlyShowsSubs = preferences.showOnlyNetworkSubscriptions;

    const includeFromPrefs = (av: Availability, network: Network) => {
      let showFromSubscriptions = onlyShowsSubs
        ? R.any(R.propEq('slug', network.slug), networkSubscriptions)
        : true;

      let hasPresentation = av.presentationType
        ? R.contains(av.presentationType, preferences.presentationTypes)
        : true;

      return showFromSubscriptions && hasPresentation;
    };

    const availabilityFilter = (av: Availability) => {
      return !R.isNil(av.network) && includeFromPrefs(av, av.network!);
    };

    let groupedByNetwork = availabilities
      ? R.groupBy(
          (av: Availability) => av.network!.slug,
          R.filter(availabilityFilter, availabilities),
        )
      : {};

    return R.values(
      R.mapObjIndexed(avs => {
        let lowestCostAv = R.head(R.sortBy(R.prop('cost'))(avs))!;
        let hasHd = R.find(R.propEq('presentationType', 'hd'), avs);
        let has4k = R.find(R.propEq('presentationType', '4k'), avs);

        let logoUri =
          '/images/logos/' + lowestCostAv.network!.slug + '/icon.jpg';

        return (
          <span key={lowestCostAv.id} style={{ margin: 10 }}>
            <Typography
              display="inline"
              style={{ display: 'flex', flexDirection: 'column' }}
            >
              <img
                key={lowestCostAv.id}
                src={logoUri}
                style={{ width: 50, borderRadius: 10 }}
              />
              {lowestCostAv.cost && (
                <Chip
                  size="small"
                  label={`$${lowestCostAv.cost}`}
                  style={{ marginTop: 5 }}
                />
              )}
            </Typography>
          </span>
        );
      }, groupedByNetwork),
    );
  };

  render() {
    let { classes, itemDetail } = this.props;
    let { openTab } = this.state;
    let availabilities: { [key: string]: Availability[] };

    if (itemDetail.availability) {
      availabilities = R.mapObjIndexed(
        R.pipe(
          R.filter<Availability, 'array'>(R.propEq('isAvailable', true)),
          R.sortBy(R.prop('cost')),
        ),
        R.groupBy(R.prop('offerType'), itemDetail.availability),
      );
    } else {
      availabilities = {};
    }

    return (
      <React.Fragment>
        <Typography color="inherit" variant="h5">
          Availability
        </Typography>

        <Tabs
          value={openTab}
          indicatorColor="primary"
          textColor="primary"
          aria-label="icon label tabs example"
          variant="fullWidth"
        >
          <Tab
            icon={<Subscriptions />}
            label="Stream"
            onClick={() => this.manageAvailabilityTabs(0)}
          />
          <Tab
            icon={<Cloud />}
            label="Rent"
            onClick={() => this.manageAvailabilityTabs(1)}
          />
          <Tab
            icon={<AttachMoney />}
            label="Buy"
            onClick={() => this.manageAvailabilityTabs(2)}
          />
        </Tabs>

        <Collapse in={openTab === 0} timeout="auto" unmountOnExit>
          {availabilities.subscription ? (
            <CardContent className={classes.availabilePlatforms}>
              {this.renderOfferDetails(availabilities.subscription)}
            </CardContent>
          ) : null}
        </Collapse>
        <Collapse in={openTab === 1} timeout="auto" unmountOnExit>
          {availabilities.rent ? (
            <CardContent className={classes.availabilePlatforms}>
              {this.renderOfferDetails(availabilities.rent)}
            </CardContent>
          ) : null}
        </Collapse>
        <Collapse in={openTab === 2} timeout="auto" unmountOnExit>
          {availabilities.buy ? (
            <CardContent className={classes.availabilePlatforms}>
              {this.renderOfferDetails(availabilities.buy)}
            </CardContent>
          ) : null}
        </Collapse>
      </React.Fragment>
    );
  }
}

export default withStyles(styles)(ThingAvailability);
