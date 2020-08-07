import { createStyles, makeStyles, Theme, Typography } from '@material-ui/core';
import React from 'react';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    filterLabel: {
      padding: theme.spacing(0.5),
    },
  }),
);

type Props = {
  title: string;
};

export default function FilterSectionTitle(props: Props) {
  const classes = useStyles();
  return (
    <Typography variant="h6" className={classes.filterLabel} display="block">
      {props.title}
    </Typography>
  );
}
