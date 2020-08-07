import { OffersFilters } from '../../utils/searchFilters';
import { Chip, createStyles, makeStyles, Theme } from '@material-ui/core';
import React, { useContext } from 'react';
import FilterSectionTitle from './FilterSectionTitle';
import { OfferType } from '../../types';
import { FilterContext } from './FilterContext';

interface Props {
  readonly handleChange: (type: OffersFilters) => void;
  readonly showTitle?: boolean;
}

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    chip: {
      margin: theme.spacing(0.25),
      flexBasis: '48%',
      justifyContent: 'center',
      flexGrow: 1,
    },
    chipContainer: {
      display: 'flex',
      flexDirection: 'row',
      width: '100%',
      flexWrap: 'wrap',
    },
    filterLabel: {
      padding: theme.spacing(0.5),
    },
    container: {
      display: 'flex',
      flexDirection: 'column',
      width: '100%',
    },
  }),
);

export default function OffersSelect(props: Props) {
  const classes = useStyles();

  const { filters } = useContext(FilterContext);
  const offers = filters.offers;

  const updateOfferType = (value?: OfferType) => {
    let newValue = value ? [value] : undefined;
    props.handleChange({ ...offers, types: newValue });
  };

  const isTypeAll = !offers?.types || !offers?.types.length;
  const isSubscription =
    offers?.types && offers?.types!.includes(OfferType.subscription);
  const isRent = offers?.types && offers?.types!.includes(OfferType.rent);
  const isBuy = offers?.types && offers?.types!.includes(OfferType.buy);

  return (
    <div className={classes.container}>
      {props.showTitle && <FilterSectionTitle title="Offers" />}
      <div className={classes.chipContainer}>
        <Chip
          key={'all'}
          onClick={() => updateOfferType(undefined)}
          size="medium"
          color={isTypeAll ? 'primary' : 'secondary'}
          label="All"
          className={classes.chip}
        />
        <Chip
          key={'subscription'}
          onClick={() => updateOfferType(OfferType.subscription)}
          size="medium"
          color={isSubscription ? 'primary' : 'secondary'}
          label="Subscription"
          className={classes.chip}
        />
        <Chip
          key={'rent'}
          onClick={() => updateOfferType(OfferType.rent)}
          size="medium"
          color={isRent ? 'primary' : 'secondary'}
          label="Rent"
          className={classes.chip}
        />
        <Chip
          key={'buy'}
          onClick={() => updateOfferType(OfferType.buy)}
          size="medium"
          color={isBuy ? 'primary' : 'secondary'}
          label="Buy"
          className={classes.chip}
        />
      </div>
    </div>
  );
}

OffersSelect.defaultProps = {
  showTitle: true,
};
