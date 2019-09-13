import {
  CardContent,
  Chip,
  Collapse,
  makeStyles,
  Tabs,
  Tab,
  Theme,
  Typography,
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
import React from 'react';
import Thing from '../types/Thing';
import { UserSelf } from '../reducers/user';
import { ApiItem, ItemAvailability } from '../types/v2';
import { AppState } from '../reducers';
import { connect } from 'react-redux';
import _ from 'lodash';

const useStyles = makeStyles((theme: Theme) => ({
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
}));

interface Props {
  itemDetail: ApiItem;
  userSelf?: UserSelf;
  networks?: Network[];
}

const ThingAvailability = (props: Props) => {
  const classes = useStyles();

  const { itemDetail, userSelf } = props;
  let availabilities: { [key: string]: ItemAvailability[] };

  if (itemDetail.availability) {
    availabilities = R.mapObjIndexed(
      R.pipe(
        // TODO: This calculation isn't exactly correct, fix it...
        R.filter<ItemAvailability, 'array'>(
          av => !av.start_date && !av.end_date,
        ),
        R.sortBy(R.prop('cost')),
      ),
      R.groupBy(R.prop('offer_type'), itemDetail.availability || []),
    );
  } else {
    availabilities = {};
  }

  let x: [ItemAvailability[], number][] = [
    [availabilities.theater, 0],
    [availabilities.subscription, 1],
    [availabilities.rent, 2],
    [availabilities.buy, 2],
  ];
  let firstAvailable = R.find(([av, idx]) => {
    return av && av.length > 0;
  }, x);

  const [openTab, setOpenTab] = React.useState<number>(
    firstAvailable ? firstAvailable[1] : 0,
  );

  const renderOfferDetails = (availabilities: ItemAvailability[]) => {
    let onlyShowsSubs = false;

    if (userSelf && userSelf.preferences && userSelf.networks) {
      onlyShowsSubs = userSelf.preferences.showOnlyNetworkSubscriptions;
    }

    const includeFromPrefs = (av: ItemAvailability, networkId: number) => {
      if (userSelf) {
        let showFromSubscriptions = onlyShowsSubs
          ? R.any(R.propEq('id', networkId), userSelf!.networks)
          : true;

        let hasPresentation = av.presentation_type
          ? R.contains(
              av.presentation_type,
              userSelf!.preferences.presentationTypes,
            )
          : true;

        return showFromSubscriptions && hasPresentation;
      } else {
        return true;
      }
    };

    const availabilityFilter = (av: ItemAvailability) => {
      return !R.isNil(av.network_id) && includeFromPrefs(av, av.network_id!);
    };

    console.log(props.networks);
    let groupedByNetwork = availabilities
      ? R.groupBy(
          (av: ItemAvailability) =>
            _.find(props.networks || [], n => n.id === av.network_id)!.slug,
          R.filter(availabilityFilter, availabilities),
        )
      : {};

    // TODO: Fix id - give availabilities IDs
    let i = 0;
    return R.values(
      R.mapObjIndexed(avs => {
        let lowestCostAv = R.head(R.sortBy(R.prop('cost'))(avs))!;
        let hasHd = R.find(R.propEq('presentation_type', 'hd'), avs);
        let has4k = R.find(R.propEq('presentation_type', '4k'), avs);
        // let logoUri =
        //   '/images/logos/' + lowestCostAv.network!.slug + '/icon.jpg';

        // TODO: Need logo UI
        return (
          <div className={classes.platform} key={i}>
            {/* <img src={logoUri} className={classes.logo} /> */}
            {lowestCostAv.cost && (
              <Chip
                size="medium"
                label={`$${lowestCostAv.cost}`}
                className={classes.genre}
              />
            )}
          </div>
        );
      }, groupedByNetwork),
    );
  };

  return props.networks ? (
    <React.Fragment>
      <Typography color="inherit" variant="h5">
        Availability
      </Typography>
      {(availabilities.theater && availabilities.theater.length > 0) ||
      (availabilities.subscription && availabilities.subscription.length > 0) ||
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
              onClick={() => setOpenTab(0)}
              disabled={
                !(availabilities.theater && availabilities.theater.length)
              }
              style={{ whiteSpace: 'nowrap' }}
            />
            <Tab
              icon={<Cloud />}
              label="Stream"
              onClick={() => setOpenTab(1)}
              disabled={
                !(
                  availabilities.subscription &&
                  availabilities.subscription.length
                )
              }
              style={{ whiteSpace: 'nowrap' }}
            />
            <Tab
              icon={<Visibility />}
              label="Rent"
              onClick={() => setOpenTab(2)}
              disabled={!(availabilities.rent && availabilities.rent.length)}
              style={{ whiteSpace: 'nowrap' }}
            />
            <Tab
              icon={<AttachMoney />}
              label="Buy"
              onClick={() => setOpenTab(3)}
              disabled={!(availabilities.buy && availabilities.buy.length)}
              style={{ whiteSpace: 'nowrap' }}
            />
          </Tabs>
          <Collapse in={openTab === 0} timeout="auto" unmountOnExit>
            {availabilities.theater ? (
              <CardContent className={classes.availabilePlatforms}>
                {renderOfferDetails(availabilities.theater)}
              </CardContent>
            ) : null}
          </Collapse>
          <Collapse in={openTab === 1} timeout="auto" unmountOnExit>
            {availabilities.subscription ? (
              <CardContent className={classes.availabilePlatforms}>
                {renderOfferDetails(availabilities.subscription)}
              </CardContent>
            ) : null}
          </Collapse>
          <Collapse in={openTab === 2} timeout="auto" unmountOnExit>
            {availabilities.rent ? (
              <CardContent className={classes.availabilePlatforms}>
                {renderOfferDetails(availabilities.rent)}
              </CardContent>
            ) : null}
          </Collapse>
          <Collapse in={openTab === 3} timeout="auto" unmountOnExit>
            {availabilities.buy ? (
              <CardContent className={classes.availabilePlatforms}>
                {renderOfferDetails(availabilities.buy)}
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
                  itemDetail.original_title
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
  ) : null;
};

const mapStateToProps = (appState: AppState) => {
  return {
    networks: appState.metadata.networks,
  };
};

export default connect(mapStateToProps)(ThingAvailability);
