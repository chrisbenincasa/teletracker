import React, { Component, RefObject } from 'react';
import {
  Avatar,
  createStyles,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { ChevronLeft, ChevronRight, Person } from '@material-ui/icons';
import { parseInitials } from '../utils/textHelper';
import RouterLink from 'next/link';
import { FixedSizeList as LazyList } from 'react-window';
import AutoSizer from 'react-virtualized-auto-sizer';
import { Item, ItemCastMember } from '../types/v2/Item';
import countBy from 'ramda/es/countBy';

const styles = (theme: Theme) =>
  createStyles({
    avatar: {
      width: 100,
      height: 100,
      fontSize: '3rem',
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
      display: 'block',
      height: '100%',
      cursor: 'pointer',
    },
    actualName: {
      fontWeight: theme.typography.fontWeightBold,
      fontSize: '0.9rem',
    },
    characterName: {
      fontStyle: 'italic',
      fontSize: '0.7rem',
    },
    grid: {
      [theme.breakpoints.up('sm')]: {
        justifyContent: 'flex-start',
      },
      justifyContent: 'space-around',
      height: 180,
    },
    header: {
      padding: theme.spacing(1, 0),
      fontWeight: 700,
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

interface State {
  currentCarouselIndex: number;
  totalCast: number;
  numberCastRemaining: number;
  castPerPage: number;
}

class Cast extends Component<Props, State> {
  private listRef: React.RefObject<LazyList>;

  constructor(props) {
    super(props);
    this.listRef = React.createRef();

    this.state = {
      currentCarouselIndex: 0,
      totalCast: 0,
      numberCastRemaining: 0,
      castPerPage: 0,
    };
  }

  componentDidMount() {
    const { classes, itemDetail } = this.props;
    const credits = itemDetail.cast ? itemDetail.cast : [];
    const width = this.listRef?.current?.props?.width || 0;
    const itemSize = this.listRef?.current?.props?.itemSize || 0;
    const pageSize = Math.round(Number(width) / Number(itemSize));

    this.setState({
      totalCast: credits.length,
      castPerPage: pageSize,
      numberCastRemaining:
        credits.length - pageSize < 0 ? 0 : credits.length - pageSize,
    });
  }

  renderAvatar(castMember: ItemCastMember) {
    let { classes } = this.props;

    const WrappedAvatar = React.forwardRef(({ onClick, href }: any, ref) => {
      return (
        <a
          href={href}
          onClick={onClick}
          ref={ref as RefObject<HTMLAnchorElement>}
          className={classes.avatarLink}
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
            {castMember.person && castMember.person.profile_path ? null : (
              <Person style={{ fontSize: '5rem' }} />
            )}
          </Avatar>
          <Typography
            color="textPrimary"
            className={classes.actualName}
            align="center"
            itemProp="name"
          >
            {castMember.name}
          </Typography>
          <Typography
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
      <RouterLink
        href={'/person/[id]?id=' + castMember.slug}
        as={'/person/' + castMember.slug}
      >
        <WrappedAvatar />
      </RouterLink>
    );
  }

  setCurrentCarouselIndex = index => {
    this.setState({
      currentCarouselIndex: index,
    });
  };

  carouselNavigationPrevious = () => {
    const { currentCarouselIndex, numberCastRemaining, totalCast } = this.state;
    const width = this.listRef?.current?.props?.width || 0;
    const itemSize = this.listRef?.current?.props?.itemSize || 0;
    const pageSize = Math.round(Number(width) / Number(itemSize));
    const remainingCast =
      numberCastRemaining > 0 ? numberCastRemaining + pageSize : pageSize;

    // No need to do this if it's the first page
    // To do: don't hardcode 2 here, make it dynamic
    if (this.state.currentCarouselIndex > 0) {
      const newIndex = this.state.currentCarouselIndex - pageSize;
      this.setState(
        {
          currentCarouselIndex: newIndex < 0 ? 0 : newIndex,
          castPerPage: pageSize,
          numberCastRemaining: remainingCast < 1 ? 0 : remainingCast,
        },
        () =>
          console.log(
            'current index: ',
            this.state.currentCarouselIndex,
            ' remaining: ',
            this.state.numberCastRemaining,
          ),
      );
      this.listRef?.current?.scrollToItem(newIndex, 'start');
    } else {
      this.setState(
        {
          currentCarouselIndex: 0,
        },
        () => console.log(this.state.currentCarouselIndex),
      );
    }
  };

  carouselNavigationNext = () => {
    const { itemDetail } = this.props;
    const { numberCastRemaining, totalCast } = this.state;
    const credits = itemDetail.cast ? itemDetail.cast : [];
    const width = this.listRef?.current?.props?.width || 0;
    const itemSize = this.listRef?.current?.props?.itemSize || 0;
    const pageSize = Math.round(Number(width) / Number(itemSize));

    // No need to do this if it's the last page
    // To do: don't hardcode 2 here, make it dynamic
    if (this.state.currentCarouselIndex + pageSize < credits.length) {
      const newIndex = this.state.currentCarouselIndex + pageSize;
      const remainingCast =
        (numberCastRemaining > 0
          ? numberCastRemaining
          : credits.length - pageSize) - pageSize;

      this.setState(
        {
          currentCarouselIndex:
            newIndex > credits.length ? credits.length : newIndex,
          castPerPage: pageSize,
          numberCastRemaining: remainingCast < 0 ? 0 : remainingCast,
        },
        () =>
          console.log(
            'current index: ',
            this.state.currentCarouselIndex,
            ' remaining: ',
            this.state.numberCastRemaining,
          ),
      );
      this.listRef?.current?.scrollToItem(newIndex, 'start');
    } else {
      this.setState(
        {
          currentCarouselIndex: credits.length,
        },
        () => console.log('cast remaining', this.state.numberCastRemaining),
      );
    }
  };

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
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            justifyContent: 'flex-start',
          }}
        >
          <Typography color="inherit" variant="h5" className={classes.header}>
            Cast
          </Typography>
          <div
            style={{
              display: 'flex',
              flexDirection: 'row',
              justifyContent: 'flex-end',
              flexGrow: 1,
            }}
          >
            <ChevronLeft
              style={{
                fontSize: '2.5rem',
                cursor:
                  this.state.currentCarouselIndex === 0 ? undefined : 'pointer',
              }}
              color={
                this.state.currentCarouselIndex === 0 ? 'secondary' : undefined
              }
              onClick={this.carouselNavigationPrevious}
            />
            <ChevronRight
              style={{
                fontSize: '2.5rem',
                cursor:
                  this.state.numberCastRemaining < 1 ? undefined : 'pointer',
              }}
              color={
                this.state.numberCastRemaining < 1 ? 'secondary' : undefined
              }
              onClick={this.carouselNavigationNext}
            />
          </div>
        </div>
        <div className={classes.grid}>
          <AutoSizer>
            {({ height, width }) => (
              <LazyList
                height={190}
                itemCount={credits.length}
                itemSize={125}
                layout="horizontal"
                width={width}
                overscanCount={0}
                initialScrollOffset={0}
                style={{ overflow: 'hidden' }}
                ref={this.listRef}
              >
                {Person}
              </LazyList>
            )}
          </AutoSizer>
        </div>
      </React.Fragment>
    ) : null;
  }
}

export default withStyles(styles)(Cast);
