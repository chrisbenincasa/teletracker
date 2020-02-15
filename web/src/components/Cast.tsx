import React, { Component, RefObject } from 'react';
import {
  Avatar,
  createStyles,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { parseInitials } from '../utils/textHelper';
import RouterLink from 'next/link';
import { FixedSizeList as LazyList } from 'react-window';
import AutoSizer from 'react-virtualized-auto-sizer';
import { Item, ItemCastMember } from '../types/v2/Item';

const styles = (theme: Theme) =>
  createStyles({
    avatar: {
      width: 100,
      height: 100,
      boxShadow: theme.shadows[1],
      '&:hover': {
        opacity: 0.8,
        transition: theme.transitions.create(['backgroundColor', 'opacity'], {
          duration: theme.transitions.duration.leavingScreen,
          easing: theme.transitions.easing.easeIn,
        }),
      },
    },
    avatarLink: {
      textDecoration: 'none',
    },
    actualName: {
      fontWeight: theme.typography.fontWeightBold,
    },
    castContainer: {
      marginTop: theme.spacing(1),
    },
    characterName: {
      fontStyle: 'italic',
    },
    grid: {
      [theme.breakpoints.up('sm')]: {
        justifyContent: 'flex-start',
      },
      justifyContent: 'space-around',
      height: 220,
    },
    personContainer: {
      display: 'flex',
      flexDirection: 'column',
      maxWidth: 100,
      margin: theme.spacing(1),
    },
  });

interface OwnProps {
  itemDetail: Item;
}

type Props = OwnProps & WithStyles<typeof styles>;

class Cast extends Component<Props, {}> {
  renderAvatar(castMember: ItemCastMember) {
    let { classes } = this.props;

    const WrappedAvatar = React.forwardRef(({ onClick, href }: any, ref) => {
      return (
        <a
          href={href}
          onClick={onClick}
          ref={ref as RefObject<HTMLAnchorElement>}
          className={classes.avatarLink}
          style={{
            display: 'block',
            height: '100%',
            textDecoration: 'none',
          }}
        >
          <Avatar
            alt={`Photo of ${castMember.name}`}
            src={
              castMember.person && castMember.person.profile_path
                ? `https://image.tmdb.org/t/p/w185/${castMember.person.profile_path}`
                : ''
            }
            className={classes.avatar}
            itemProp="image"
          >
            {castMember.person && castMember.person.profile_path
              ? null
              : parseInitials(castMember.name!, 'name')}
          </Avatar>
          <Typography
            variant="subtitle1"
            color="textPrimary"
            className={classes.actualName}
            align="center"
            itemProp="name"
          >
            {castMember.name}
          </Typography>
          <Typography
            variant="subtitle2"
            color="textPrimary"
            className={classes.characterName}
            align="center"
            itemProp="character"
          >
            {castMember.character}
          </Typography>
        </a>
      );
    });

    return (
      <RouterLink href={'/person/' + castMember.slug}>
        <WrappedAvatar />
      </RouterLink>
    );
  }

  render() {
    const { classes, itemDetail } = this.props;
    const credits = itemDetail.cast ? itemDetail.cast : [];
    const Person = ({ index, style }) => (
      <div
        className={classes.personContainer}
        key={credits[index].id}
        itemProp="actor"
        itemScope
        itemType="http://schema.org/Person"
        style={style}
      >
        {this.renderAvatar(credits[index])}
      </div>
    );

    return credits && credits.length > 0 ? (
      <React.Fragment>
        <div className={classes.castContainer}>
          <Typography color="inherit" variant="h5">
            Cast
          </Typography>

          <div className={classes.grid}>
            <AutoSizer>
              {({ height, width }) => (
                <LazyList
                  height={220}
                  itemCount={credits.length}
                  itemSize={125}
                  layout="horizontal"
                  width={width}
                  style={{ overflowX: 'auto', overflowY: 'hidden' }}
                >
                  {Person}
                </LazyList>
              )}
            </AutoSizer>
          </div>
        </div>
      </React.Fragment>
    ) : null;
  }
}

export default withStyles(styles)(Cast);
