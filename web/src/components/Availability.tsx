import React from 'react';
import {
  Card,
  CardContent,
  CardMedia,
  Collapse,
  makeStyles,
  Tab,
  Tabs,
  Theme,
  Typography,
} from '@material-ui/core';
import {
  AttachMoney,
  Cloud,
  Theaters,
  TvOff,
  Visibility,
} from '@material-ui/icons';
import _ from 'lodash';
import * as R from 'ramda';
import { ItemAvailability } from '../types/v2';
import { Item } from '../types/v2/Item';
import { useWithUserContext } from '../hooks/useWithUser';
import { useNetworks } from '../hooks/useStateMetadata';
import { deepLinkForId, Platform } from '../utils/availability-utils';

const useStyles = makeStyles((theme: Theme) => ({
  availabilityContainer: {
    display: 'flex',
    flexDirection: 'column',
    [theme.breakpoints.up('sm')]: {
      justifyContent: 'center',
    },
  },
  availabilityWrapper: {
    maxWidth: 1000,
    width: '75%',
  },
  availabilePlatforms: {
    display: 'flex',
    justifyContent: 'flex-start',
    [theme.breakpoints.down('sm')]: {
      justifyContent: 'center',
    },
    flexWrap: 'wrap',
  },
  genre: {
    marginTop: theme.spacing(1),
  },
  header: {
    padding: theme.spacing(1, 0),
    fontWeight: 700,
  },
  logo: {
    width: 50,
    borderRadius: theme.shape.borderRadius,
  },
  platform: {
    display: 'flex',
    flexDirection: 'column',
    margin: theme.spacing(1),
  },
  unavailableContainer: {
    display: 'flex',
    flexDirection: 'row',
  },
  cardRoot: {
    display: 'flex',
    flexBasis: '33.33333333%',
    backgroundColor: 'rgba(66, 66, 66, 0.5)',
    padding: 8,
    border: '1px solid transparent',
    transition:
      'boder-color 100ms, box-shadow 300ms cubic-bezier(0.4, 0, 0.2, 1) 0ms',
    '&:hover': {
      borderColor: theme.palette.primary.main,
      cursor: 'pointer',
    },
  },
}));

interface Props {
  itemDetail: Item;
}

const Availability = (props: Props) => {
  const classes = useStyles();
  const { userSelf } = useWithUserContext();
  const networks = useNetworks();

  const { itemDetail } = props;
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

  const getDeepLink = (availability: ItemAvailability) => {
    const network = _.find(networks, { id: availability.network_id });

    if (network) {
      const externalId = _.find(itemDetail.external_ids || [], {
        provider: network.slug,
      });

      if (externalId) {
        return deepLinkForId(
          externalId.id,
          itemDetail.type,
          network.slug,
          Platform.web,
        );
      }
    }
  };

  const clickAvailability = (availability: ItemAvailability) => {
    const link = getDeepLink(availability);

    if (link) {
      window.open(link, '_blank', 'width=1280,height=720');
    }
  };

  const renderOfferDetails = (availabilities: ItemAvailability[]) => {
    let onlyShowsSubs = false;

    if (userSelf && userSelf.preferences && userSelf.networks) {
      onlyShowsSubs = userSelf.preferences.showOnlyNetworkSubscriptions;
    }

    const includeFromPrefs = (av: ItemAvailability, networkId: number) => {
      if (userSelf) {
        let showFromSubscriptions = onlyShowsSubs
          ? R.any(R.propEq('id', networkId), userSelf!.networks || [])
          : true;

        let hasPresentation = av.presentation_type
          ? R.contains(
              av.presentation_type,
              userSelf!.preferences?.presentationTypes || [],
            )
          : true;

        return showFromSubscriptions && hasPresentation;
      } else {
        return true;
      }
    };

    const availabilityFilter = (av: ItemAvailability) => {
      return !R.isNil(av.network_id); // && includeFromPrefs(av, av.network_id!);
    };

    let groupedByNetwork = availabilities
      ? R.groupBy(
          (av: ItemAvailability) =>
            _.find(networks || [], n => n.id === av.network_id)!.slug,
          R.filter(availabilityFilter, availabilities),
        )
      : {};

    // TODO: Fix id - give availabilities IDs
    // currently using index for keying purposes

    return R.values(
      R.mapObjIndexed((avs, index) => {
        let lowestCostAv = R.head(R.sortBy(R.prop('cost'))(avs))!;
        let network = _.find(networks!, { id: lowestCostAv.network_id });

        if (!network) {
          return null;
        }

        let logoUri = '/images/logos/' + network!.slug + '/icon.jpg';

        return (
          <Card
            className={classes.cardRoot}
            onClick={() => clickAvailability(lowestCostAv)}
            key={index}
          >
            <CardMedia
              style={{ width: 60, borderRadius: 4 }}
              image={logoUri}
              title={network.name}
            />
            <div
              style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
              }}
            >
              <CardContent style={{ padding: '0 16px' }}>
                <Typography>{network.name}</Typography>
              </CardContent>
            </div>
          </Card>
        );

        // return (
        //   <div className={classes.platform} key={index}>
        //     <img src={logoUri} className={classes.logo} />
        //     {lowestCostAv.cost && (
        //       <Chip
        //         size="medium"
        //         label={`$${lowestCostAv.cost}`}
        //         className={classes.genre}
        //       />
        //     )}
        //   </div>
        // );
      }, groupedByNetwork),
    );
  };

  return networks ? (
    <div className={classes.availabilityWrapper}>
      <Typography color="inherit" variant="h5" className={classes.header}>
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
              disabled={(availabilities.theater || []).length === 0}
              disableRipple
              style={{ whiteSpace: 'nowrap' }}
            />
            <Tab
              icon={<Cloud />}
              label="Stream"
              onClick={() => setOpenTab(1)}
              disabled={(availabilities.subscription || []).length === 0}
              disableRipple
              style={{ whiteSpace: 'nowrap' }}
            />
            <Tab
              icon={<Visibility />}
              label="Rent"
              onClick={() => setOpenTab(2)}
              disabled={(availabilities.rent || []).length === 0}
              disableRipple
              style={{ whiteSpace: 'nowrap' }}
            />
            <Tab
              icon={<AttachMoney />}
              label="Buy"
              onClick={() => setOpenTab(3)}
              disabled={(availabilities.buy || []).length === 0}
              disableRipple
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
        <div className={classes.unavailableContainer}>
          <TvOff fontSize="large" style={{ margin: 8 }} />
          <div className={classes.availabilityContainer}>
            <Typography variant="subtitle1">
              {`${itemDetail.canonicalTitle} is not currently available to stream, rent, or purchase.`}
              {/* {`Add it to your list and we'll notify you when it becomes available!`} */}
            </Typography>
          </div>
        </div>
      )}
    </div>
  ) : null;
};

export default React.memo(Availability, (prevProps, nextProps) => {
  return _.isEqual(prevProps, nextProps);
});
