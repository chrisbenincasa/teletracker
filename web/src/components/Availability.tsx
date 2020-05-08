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
import { AttachMoney, Cloud, TvOff, Visibility } from '@material-ui/icons';
import _ from 'lodash';
import { ItemAvailability } from '../types/v2';
import { Item } from '../types/v2/Item';
import { useNetworks } from '../hooks/useStateMetadata';
import { deepLinkForId, Platform } from '../utils/availability-utils';
import { OfferType } from '../types';

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
  const networks = useNetworks();

  const { itemDetail } = props;

  const findAvailabilitiesWithOfferType = (offerType: OfferType) => {
    return _.flatten(
      _.map(itemDetail.availability || [], av =>
        _.filter(av.offers, { offerType }),
      ),
    );
  };

  const tabs = {
    [OfferType.subscription]: 0,
    [OfferType.rent]: 1,
    [OfferType.buy]: 2,
  };

  const firstAvailable: string | undefined = _.find(
    Object.keys(OfferType),
    offerType =>
      findAvailabilitiesWithOfferType(OfferType[offerType]).length > 0,
  );

  let hasSubscriptionOffers =
    findAvailabilitiesWithOfferType(OfferType.subscription).length > 0;
  let hasRentalOffers =
    findAvailabilitiesWithOfferType(OfferType.rent).length > 0;
  let hasBuyOffers = findAvailabilitiesWithOfferType(OfferType.buy).length > 0;
  const hasAnyAvailabilities =
    hasSubscriptionOffers || hasRentalOffers || hasBuyOffers;

  console.log(firstAvailable);

  const [openTab, setOpenTab] = React.useState<number>(
    firstAvailable ? tabs[firstAvailable] : 0,
  );

  const getDeepLink = (availability: ItemAvailability) => {
    const network = _.find(networks, { id: availability.networkId });

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

  const renderOfferDetails = (offerType: OfferType) => {
    return _.compact(
      _.map(
        itemDetail.availability || [],
        (availability: ItemAvailability, index) => {
          let network = _.find(networks!, { id: availability.networkId });

          if (!network) {
            return;
          }

          const logoUri = '/images/logos/' + network!.slug + '/icon.jpg';

          const offersOfType = _.filter(availability.offers, { offerType });

          if (offersOfType.length === 0) {
            return;
          }

          return (
            <Card
              className={classes.cardRoot}
              onClick={() => clickAvailability(availability)}
              key={index}
            >
              <CardMedia
                style={{ width: 60, borderRadius: 4 }}
                image={logoUri}
                title={network!.name}
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
                  <Typography>{network!.name}</Typography>
                </CardContent>
              </div>
            </Card>
          );
        },
      ),
    );
  };

  return networks ? (
    <div className={classes.availabilityWrapper}>
      <Typography color="inherit" variant="h5" className={classes.header}>
        Availability
      </Typography>
      {hasAnyAvailabilities ? (
        <React.Fragment>
          <Tabs
            value={openTab}
            indicatorColor="primary"
            textColor="primary"
            aria-label="Availibility"
            variant="fullWidth"
          >
            <Tab
              icon={<Cloud />}
              label="Stream"
              onClick={() => setOpenTab(0)}
              disabled={!hasSubscriptionOffers}
              disableRipple
              style={{ whiteSpace: 'nowrap' }}
            />
            <Tab
              icon={<Visibility />}
              label="Rent"
              onClick={() => setOpenTab(1)}
              disabled={!hasRentalOffers}
              disableRipple
              style={{ whiteSpace: 'nowrap' }}
            />
            <Tab
              icon={<AttachMoney />}
              label="Buy"
              onClick={() => setOpenTab(2)}
              disabled={!hasBuyOffers}
              disableRipple
              style={{ whiteSpace: 'nowrap' }}
            />
          </Tabs>
          <Collapse in={openTab === 0} timeout="auto" unmountOnExit>
            {hasSubscriptionOffers ? (
              <CardContent className={classes.availabilePlatforms}>
                {renderOfferDetails(OfferType.subscription)}
              </CardContent>
            ) : null}
          </Collapse>
          <Collapse in={openTab === 1} timeout="auto" unmountOnExit>
            {hasRentalOffers ? (
              <CardContent className={classes.availabilePlatforms}>
                {renderOfferDetails(OfferType.rent)}
              </CardContent>
            ) : null}
          </Collapse>
          <Collapse in={openTab === 2} timeout="auto" unmountOnExit>
            {hasBuyOffers ? (
              <CardContent className={classes.availabilePlatforms}>
                {renderOfferDetails(OfferType.buy)}
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
