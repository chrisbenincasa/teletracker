import React, { useContext } from 'react';
import {
  Chip,
  createStyles,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import { ItemType } from '../../types';
import { FilterContext } from './FilterContext';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    chip: {
      margin: theme.spacing(0.25),
      flexGrow: 1,
    },
    chipContainer: {
      display: 'flex',
      flexDirection: 'row',
      flexWrap: 'wrap',
    },
    filterLabel: {
      paddingBottom: theme.spacing(0.5),
    },
    typeContainer: {
      display: 'flex',
      flexDirection: 'column',
      width: '100%',
    },
  }),
);

interface Props {
  readonly showTitle?: boolean;
  readonly handleChange: (type?: ItemType[]) => void;
}

export default function TypeToggle(props: Props) {
  const classes = useStyles();
  const { filters } = useContext(FilterContext);
  const selectedTypes = filters.itemTypes;

  const updateTypes = (param: string, value?: ItemType[]) => {
    props.handleChange(value);
  };

  const isTypeAll = !selectedTypes || !selectedTypes.length;
  const isTypeMovie = selectedTypes && selectedTypes.includes('movie');
  const isTypeShow = selectedTypes && selectedTypes.includes('show');

  return (
    <div className={classes.typeContainer}>
      {props.showTitle && (
        <Typography className={classes.filterLabel} display="block">
          Type
        </Typography>
      )}
      <div className={classes.chipContainer}>
        <Chip
          key={'all'}
          onClick={() => updateTypes('type', undefined)}
          size="medium"
          color={isTypeAll ? 'primary' : 'secondary'}
          label="All"
          className={classes.chip}
        />
        <Chip
          key={'Movies'}
          onClick={() => updateTypes('type', ['movie'])}
          size="medium"
          color={isTypeMovie ? 'primary' : 'secondary'}
          label="Movies"
          className={classes.chip}
        />
        <Chip
          key={'TV'}
          onClick={() => updateTypes('type', ['show'])}
          size="medium"
          color={isTypeShow ? 'primary' : 'secondary'}
          label="TV"
          className={classes.chip}
        />
      </div>
    </div>
  );
}

TypeToggle.defaultProps = {
  showTitle: true,
};
