import React, { useState } from 'react';
import { Button, createStyles, makeStyles, Theme } from '@material-ui/core';
import { Tune } from '@material-ui/icons';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    settings: {
      whiteSpace: 'nowrap',
    },
  }),
);

interface Props {
  onClick: () => void;
  style?: object;
}

export default function ShowFiltersButton(props: Props) {
  const [showFilters, setShowFilters] = useState<boolean>(false);
  const classes = useStyles();
  const filtersCTA = showFilters ? 'Hide Filters' : 'Filters';

  const toggleFilters = () => {
    props.onClick();
    setShowFilters(!showFilters);
  };

  return (
    <Button
      size="small"
      onClick={toggleFilters}
      variant="contained"
      aria-label={filtersCTA}
      startIcon={<Tune />}
      className={classes.settings}
      style={props.style}
    >
      {filtersCTA}
    </Button>
  );
}
