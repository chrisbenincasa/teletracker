import React from 'react';
import {
  Card,
  CardContent,
  CardMedia,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import { TvOff } from '@material-ui/icons';
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
    maxWidth: 500,
    width: '100%',
  },
  availabilePlatforms: {
    display: 'flex',
    justifyContent: 'flex-start',
    [theme.breakpoints.down('sm')]: {
      justifyContent: 'center',
    },
    flexWrap: 'wrap',
    padding: theme.spacing(0),
    '&:last-child': {
      paddingBottom: theme.spacing(0),
    },
  },
  cardRoot: {
    display: 'flex',
    flexBasis: '100%',
    backgroundColor: 'rgba(66, 66, 66, 0.5)',
    padding: theme.spacing(1),
    margin: theme.spacing(0.5, 0),
    border: '1px solid transparent',
    transition:
      'boder-color 100ms, box-shadow 300ms cubic-bezier(0.4, 0, 0.2, 1) 0ms',
    '&:hover': {
      borderColor: theme.palette.primary.main,
      cursor: 'pointer',
    },
  },
  cardContent: {
    padding: theme.spacing(0, 2),
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    '&:last-child': {
      paddingBottom: theme.spacing(0),
    },
  },
  genre: {
    marginTop: theme.spacing(1),
  },
  header: {
    padding: theme.spacing(1, 0),
    fontWeight: 700,
  },
  link: {
    marginRight: theme.spacing(0.5),
    [theme.breakpoints.down('sm')]: {
      width: '100%',
    },
  },
  logo: {
    width: 50,
    borderRadius: theme.shape.borderRadius,
  },
  networkLogo: {
    width: 60,
    borderRadius: 4,
  },
  platform: {
    display: 'flex',
    flexDirection: 'column',
    margin: theme.spacing(1),
  },
  unavailableIcon: {
    width: 60,
    borderRadius: 4,
    display: 'flex',
    alignSelf: 'center',
  },
  unavailableCard: {
    display: 'flex',
    flexBasis: '100%',
    backgroundColor: 'rgba(66, 66, 66, 0.5)',
    padding: theme.spacing(1),
    margin: theme.spacing(0.5, 0),
    border: '1px solid transparent',
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

          const getCleanOfferTitle = (offerType: string) => {
            if (offerType === 'subscription') {
              return 'Stream on';
            } else if (offerType === 'buy') {
              return 'Buy on';
            } else if (offerType === 'rent') {
              return 'Rent on';
            } else if (offerType === 'theater') {
              return 'In theaters now! Get tickets on';
            } else if (offerType === 'free') {
              return 'Free on';
            } else if (offerType === 'aggregate') {
              return 'Watch all seasons on';
            } else {
              return 'Watch on ';
            }
          };

          const cleanOfferTitle = getCleanOfferTitle(offerType);
          const link = getDeepLink(availability);

          return (
            <a
              href={link ? link : '#'}
              target="_blank"
              className={classes.link}
            >
              <Card className={classes.cardRoot} key={index}>
                <CardMedia
                  className={classes.networkLogo}
                  image={logoUri}
                  title={network!.name}
                />
                <CardContent className={classes.cardContent}>
                  <Typography>{`${cleanOfferTitle} ${
                    network!.name
                  }`}</Typography>
                </CardContent>
              </Card>
            </a>
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
          {hasSubscriptionOffers ? (
            <CardContent className={classes.availabilePlatforms}>
              {renderOfferDetails(OfferType.subscription)}
            </CardContent>
          ) : null}
          {hasRentalOffers ? (
            <CardContent className={classes.availabilePlatforms}>
              {renderOfferDetails(OfferType.rent)}
            </CardContent>
          ) : null}
          {hasBuyOffers ? (
            <CardContent className={classes.availabilePlatforms}>
              {renderOfferDetails(OfferType.buy)}
            </CardContent>
          ) : null}
        </React.Fragment>
      ) : (
        <div className={classes.availabilityWrapper}>
          <CardContent className={classes.availabilePlatforms}>
            <Card className={classes.unavailableCard} key="unavailable">
              <TvOff className={classes.unavailableIcon} />
              <CardContent className={classes.cardContent}>
                <Typography>
                  {`${itemDetail.canonicalTitle} is not currently available to stream, rent, or purchase.`}
                </Typography>
              </CardContent>
            </Card>
          </CardContent>
        </div>
      )}
    </div>
  ) : null;
};

export default React.memo(Availability, (prevProps, nextProps) => {
  return _.isEqual(prevProps, nextProps);
});
