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
import {
  AttachMoney,
  Cloud,
  Visibility,
  TvOff,
  Theaters,
} from '@material-ui/icons';
import * as R from 'ramda';
import { Availability, Network } from '../types';
import React, { Component } from 'react';
import Thing from '../types/Thing';
import { UserSelf } from '../reducers/user';

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
    genre: { marginTop: 5 },
    logo: { width: 50, borderRadius: 10 },
    platform: { display: 'flex', flexDirection: 'column', margin: 10 },
  });

interface Props extends WithStyles<typeof styles> {
  itemDetail: Thing;
  userSelf: UserSelf;
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

  manageAvailabilityTabs = (value: number) => {
    this.setState({ openTab: value });
  };

  renderOfferDetails = (availabilities: Availability[]) => {
    let { classes, userSelf } = this.props;

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
          <div className={classes.platform} key={lowestCostAv.id}>
            <img src={logoUri} className={classes.logo} />
            {lowestCostAv.cost && (
              <Chip
                size="small"
                label={`$${lowestCostAv.cost}`}
                className={classes.genre}
              />
            )}
          </div>
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
        {(availabilities.theater && availabilities.theater.length > 0) ||
        (availabilities.subscription &&
          availabilities.subscription.length > 0) ||
        (availabilities.rent && availabilities.rent.length > 0) ||
        (availabilities.buy && availabilities.buy.length > 0) ? (
          <React.Fragment>
            <Tabs
              value={openTab}
              indicatorColor="primary"
              textColor="primary"
              aria-label="Availibility"
              variant="fullWidth"
            >
              <Tab
                icon={<Theaters />}
                label="Theaters"
                onClick={() => this.manageAvailabilityTabs(0)}
                disabled={
                  !(availabilities.theater && availabilities.theater.length)
                }
              />
              <Tab
                icon={<Cloud />}
                label="Stream"
                onClick={() => this.manageAvailabilityTabs(1)}
                disabled={
                  !(
                    availabilities.subscription &&
                    availabilities.subscription.length
                  )
                }
              />
              <Tab
                icon={<Visibility />}
                label="Rent"
                onClick={() => this.manageAvailabilityTabs(2)}
                disabled={!(availabilities.rent && availabilities.rent.length)}
              />
              <Tab
                icon={<AttachMoney />}
                label="Buy"
                onClick={() => this.manageAvailabilityTabs(3)}
                disabled={!(availabilities.buy && availabilities.buy.length)}
              />
            </Tabs>
            <Collapse in={openTab === 0} timeout="auto" unmountOnExit>
              {availabilities.theater ? (
                <CardContent className={classes.availabilePlatforms}>
                  {this.renderOfferDetails(availabilities.theater)}
                </CardContent>
              ) : null}
            </Collapse>
            <Collapse in={openTab === 1} timeout="auto" unmountOnExit>
              {availabilities.subscription ? (
                <CardContent className={classes.availabilePlatforms}>
                  {this.renderOfferDetails(availabilities.subscription)}
                </CardContent>
              ) : null}
            </Collapse>
            <Collapse in={openTab === 2} timeout="auto" unmountOnExit>
              {availabilities.rent ? (
                <CardContent className={classes.availabilePlatforms}>
                  {this.renderOfferDetails(availabilities.rent)}
                </CardContent>
              ) : null}
            </Collapse>
            <Collapse in={openTab === 3} timeout="auto" unmountOnExit>
              {availabilities.buy ? (
                <CardContent className={classes.availabilePlatforms}>
                  {this.renderOfferDetails(availabilities.buy)}
                </CardContent>
              ) : null}
            </Collapse>
          </React.Fragment>
        ) : (
          <React.Fragment>
            <div
              style={{
                display: 'flex',
                flexDirection: 'row',
                justifyContent: 'center',
              }}
            >
              <TvOff fontSize="large" style={{ margin: 10 }} />
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                <Typography variant="subtitle1">
                  {`${
                    itemDetail.name
                  } is not currently available to stream, rent,
                  or purchase.`}
                </Typography>
                <Typography variant="subtitle1">
                  Add it to your list and we'll be sure to notify you when it
                  becomes available!
                </Typography>
              </div>
            </div>
          </React.Fragment>
        )}
      </React.Fragment>
    );
  }
}

export default withStyles(styles)(ThingAvailability);
